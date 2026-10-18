<#
.SYNOPSIS
悦选本地下单 k6 一致性验收脚本。

.DESCRIPTION
只操作 Docker Compose 中的 yuexuan_local_service 演示库：
1. 读取 SKU 当前库存（只读，不会修改库存）；
2. 调用仅 dev 环境启用的 k6 压测用户准备接口；
3. 运行 k6；
4. 等待 Outbox 投递器扫描后运行只读一致性 SQL；
5. 将摘要保存到 docs/performance/runs/（该目录被 Git 忽略）。
eg:  .\docs\performance\run-order-test.ps1 -DishId 50 -SkuId 38 -Vus 50
前置条件：Docker 与后端已启动，且 application-dev.yml 中已设置
sky.performance.enabled: true。脚本不会自行修改配置或重启后端。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$DishId,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$SkuId,

    [ValidateRange(1, 200)]
    [int]$Vus = 100,

    [string]$BaseUrl = 'http://localhost:8080',
    [string]$MysqlContainer = 'yuexuan-mysql',
    [string]$RunId = "order-stock-$((Get-Date).ToString('yyyyMMddHHmmss'))"
)

$ErrorActionPreference = 'Stop'

$runDirectory = Join-Path $PSScriptRoot 'runs'
$userFile = Join-Path $PSScriptRoot 'vu-data.local.json'
$summaryFile = Join-Path $runDirectory "$RunId-k6-summary.json"
$checkFile = Join-Path $runDirectory "$RunId-consistency-check.sql"
$checkOutputFile = Join-Path $runDirectory "$RunId-consistency-result.txt"
$templateFile = Join-Path $PSScriptRoot 'order-consistency-check.sql'

function Write-Utf8WithoutBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    # Windows PowerShell 5.1 的 -Encoding 默认行为会误读无 BOM UTF-8，
    # 而 MySQL 客户端读取 SQL 时又不需要 BOM；这里显式使用 UTF-8 无 BOM。
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-K6MetricCount {
    param(
        [Parameter(Mandatory = $true)]$Metric,
        [Parameter(Mandatory = $true)][string]$MetricName
    )

    # k6 不同版本的 --summary-export 结构不同：
    # 旧版为 values.count，新版（包括本机 k6 v2.2.0）为 count。
    if ($null -ne $Metric.values -and $null -ne $Metric.values.count) {
        return [int]$Metric.values.count
    }
    if ($null -ne $Metric.count) {
        return [int]$Metric.count
    }
    throw "k6 摘要中未找到指标 $MetricName 的 count 字段。"
}

function Invoke-DockerMysqlQuery {
    param([Parameter(Mandatory = $true)][string]$Query)

    # SQL 经标准输入传给容器内 MySQL，避免 PowerShell 对嵌套引号的处理差异。
    # MYSQL_PWD 从容器环境变量读取；2>/dev/null 仅隐藏 MySQL 的命令行密码安全提示。
    $raw = $Query | & docker exec -i $MysqlContainer sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -N -B -uroot yuexuan_local_service 2>/dev/null'
    if ($LASTEXITCODE -ne 0) {
        throw "读取 Docker MySQL 失败（退出码 $LASTEXITCODE）。查询：$Query"
    }
    $rows = @()
    foreach ($line in @($raw)) {
        $s = [string]$line
        if ($s.Trim() -ne '') { $rows += $s }
    }
    return $rows
}

function Assert-CommandAvailable {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "未找到命令 '$Name'。请先安装或启动对应的本地依赖。"
    }
}

try {
    Assert-CommandAvailable 'k6'
    Assert-CommandAvailable 'docker'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

    if ($RunId -notmatch '^[A-Za-z0-9-]{1,32}$') {
        throw 'RunId 只能包含字母、数字和连字符，长度为 1 到 32。'
    }

    Write-Host "[1/5] 检查 Docker 演示库中的 SKU $SkuId ..." -ForegroundColor Cyan
    $skuRow = @(Invoke-DockerMysqlQuery "SELECT stock, status FROM product_sku WHERE id=$SkuId;")
    if ($skuRow.Count -ne 1) {
        throw "未找到唯一 SKU：$SkuId。请检查 -SkuId 参数。"
    }
    $columns = $skuRow[0].Trim() -split "`t"
    $stockBefore = [int]$columns[0]
    $skuStatus = [int]$columns[1]

    if ($skuStatus -ne 1) {
        throw "SKU $SkuId 当前未上架（status=$skuStatus）。请选择一个上架 SKU。"
    }
    if ($stockBefore -lt $Vus) {
        throw "SKU $SkuId 当前库存为 $stockBefore，小于 VUs=$Vus。脚本不会自动修改库存，请先人工准备实验数据。"
    }

    Write-Host "[2/5] 准备 $Vus 个独立 k6 压测用户（runId=$RunId）..." -ForegroundColor Cyan
    try {
        $prepared = Invoke-RestMethod -Method Post `
            -Uri "$BaseUrl/_dev/performance/k6-users?runId=$RunId&count=$Vus&dishId=$DishId&skuId=$SkuId"
    } catch {
        throw "压测用户准备接口不可用。请确认后端已使用 dev profile 启动且 sky.performance.enabled=true。原始错误：$($_.Exception.Message)"
    }

    if ($prepared.code -ne 1 -or $null -eq $prepared.data -or $prepared.data.Count -ne $Vus) {
        throw "压测用户准备失败：后端没有返回 $Vus 条用户数据。"
    }

    # 此文件含 JWT，已被 Git 忽略；绝不向控制台输出 Token。
    Write-Utf8WithoutBom -Path $userFile -Content ($prepared.data | ConvertTo-Json -Depth 4)

    Write-Host '[3/5] 运行 k6“加购 → 下单”测试 ...' -ForegroundColor Cyan
    $startedAt = Get-Date
    $env:K6_TEST_USERS_JSON = Get-Content -Raw $userFile
    $env:RUN_ID = $RunId
    $env:VUS = "$Vus"

    & k6 run --summary-export $summaryFile (Join-Path $PSScriptRoot 'order-submit-k6.js')
    $k6ExitCode = $LASTEXITCODE
    $endedAt = Get-Date
    Remove-Item Env:K6_TEST_USERS_JSON, Env:RUN_ID, Env:VUS -ErrorAction SilentlyContinue

    if (-not (Test-Path $summaryFile)) {
        throw 'k6 未生成摘要文件，无法继续一致性校验。'
    }

    $summary = Get-Content -Raw -Encoding UTF8 $summaryFile | ConvertFrom-Json
    $createdOrders = Get-K6MetricCount -Metric $summary.metrics.created_orders -MetricName 'created_orders'

    Write-Host '[4/5] 等待 12 秒，使 Outbox 投递器至少扫描一次 ...' -ForegroundColor Cyan
    Start-Sleep -Seconds 12

    Write-Host '[5/5] 运行 Docker MySQL 只读一致性校验 ...' -ForegroundColor Cyan
    # 必须显式 UTF-8 读取；Windows PowerShell 5.1 对无 BOM UTF-8 的默认编码并不可靠。
    $template = Get-Content -Raw -Encoding UTF8 $templateFile
    $template = $template -replace "SET @run_id = '[^']*';", "SET @run_id = '$RunId';"
    $template = $template -replace "SET @started_at = '[^']*';", "SET @started_at = '$($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))';"
    # 为 Outbox 创建事件额外预留一分钟；订单仍用 runId 精确限定。
    $template = $template -replace "SET @ended_at = '[^']*';", "SET @ended_at = '$($endedAt.AddMinutes(1).ToString('yyyy-MM-dd HH:mm:ss'))';"
    $template = $template -replace 'SET @sku_id = \d+;', "SET @sku_id = $SkuId;"
    $template = $template -replace 'SET @stock_before = \d+;', "SET @stock_before = $stockBefore;"
    $template = $template -replace 'SET @expected_successes = \d+;', "SET @expected_successes = $createdOrders;"

    Write-Utf8WithoutBom -Path $checkFile -Content $template

    # 不把 Tee-Object 与 docker 放在同一原生命令管道里：Windows PowerShell 5.1
    # 在这种组合下可能丢失外部进程的真实退出码，造成“SQL 已执行却被误报失败”。
    # 先完整接收 MySQL stdout/stderr，再立即保存并依据 docker 的退出码判断。
    $mysqlOutput = @(Get-Content -Raw -Encoding UTF8 $checkFile |
        & docker exec -i $MysqlContainer sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot yuexuan_local_service' 2>&1)
    $mysqlExitCode = $LASTEXITCODE
    $mysqlOutputText = $mysqlOutput -join [Environment]::NewLine
    Write-Utf8WithoutBom -Path $checkOutputFile -Content $mysqlOutputText
    $mysqlOutput | ForEach-Object { Write-Output $_ }

    if ($mysqlExitCode -ne 0) {
        throw "一致性 SQL 执行失败（MySQL 退出码 $mysqlExitCode）。详情：$checkOutputFile`n$mysqlOutputText"
    }

    Write-Host ''
    Write-Host '========== 本轮验收完成 ==========' -ForegroundColor Green
    Write-Host "RUN_ID:              $RunId"
    Write-Host "SKU:                 $SkuId"
    Write-Host "压测前库存:          $stockBefore"
    Write-Host "k6 成功创建订单数:   $createdOrders"
    Write-Host "k6 退出码:           $k6ExitCode（非 0 可能只是 P95 阈值未达标，请以一致性 SQL 判断正确性）"
    Write-Host "k6 摘要:             $summaryFile"
    Write-Host "一致性校验结果:      $checkOutputFile"
    Write-Host '请检查 PASS、0 行重复结果和不存在负库存后，再记录验收结论。'
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
