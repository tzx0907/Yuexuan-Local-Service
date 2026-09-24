<template>
  <div class="flash-sale-page">
    <div class="page-head">
      <div><h2>限时购管理</h2><p>配置指定商品或服务的限时优惠活动，活动库存与每人限购独立控制。</p></div>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建限时购</el-button>
    </div>
    <div class="filter-bar">
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="load"><el-option label="未启用" :value="0"/><el-option label="进行中" :value="1"/></el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="id" label="活动编号" width="100"/>
      <el-table-column label="活动商品与规格" min-width="230"><template slot-scope="{row}"><div class="product-name">{{ row.productName || '商品信息加载失败' }}</div><div class="product-spec">{{ row.specName || '规格' }}：{{ row.specValue || '-' }}</div></template></el-table-column>
      <el-table-column label="原价" min-width="90"><template slot-scope="{row}">¥ {{ money(row.originalPrice) }}</template></el-table-column>
      <el-table-column prop="salePrice" label="限时价" min-width="100"><template slot-scope="{row}">¥ {{ money(row.salePrice) }}</template></el-table-column>
      <el-table-column prop="activityStock" label="活动库存" min-width="100"/>
      <el-table-column label="剩余活动量" min-width="110"><template slot-scope="{row}">{{ row.remainingStock }} 件</template></el-table-column>
      <el-table-column prop="perUserLimit" label="每人限购" min-width="100"/>
      <el-table-column label="活动时间" min-width="300"><template slot-scope="{row}">{{ row.startTime }} 至 {{ row.endTime }}</template></el-table-column>
      <el-table-column label="状态" width="130"><template slot-scope="{row}"><el-tag :type="activityStateType(row)">{{ activityState(row) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="170"><template slot-scope="{row}"><el-button type="text" @click="openEdit(row)">编辑</el-button><el-button type="text" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > query.pageSize" class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.pageSize" @current-change="changePage" />
    <el-dialog :title="editing ? '编辑限时购活动' : '新建限时购活动'" :visible.sync="dialog" width="680px" :close-on-click-modal="false">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-alert title="先按商品名称搜索，再选择要参与限时购的具体规格。活动价格以该规格的原价为上限。" type="info" :closable="false" class="sku-tip"/>
        <el-form-item label="活动商品规格" prop="skuId"><el-select v-model="form.skuId" filterable remote reserve-keyword placeholder="输入商品名或规格搜索" :remote-method="searchSku" :loading="skuLoading" style="width:100%" @change="selectSku"><el-option v-for="item in skuOptions" :key="item.skuId" :label="skuLabel(item)" :value="item.skuId"><span>{{ item.productName }}</span><span class="sku-option-detail">{{ item.specName || '规格' }}：{{ item.specValue }} · 原价 ¥{{ money(item.price) }} · 库存 {{ item.stock }}</span></el-option></el-select></el-form-item>
        <el-form-item label="限时价格" prop="salePrice"><el-input-number v-model="form.salePrice" :min="0.01" :precision="2" :step="1"/></el-form-item>
        <el-form-item label="活动库存" prop="activityStock"><el-input-number v-model="form.activityStock" :min="1" :precision="0"/></el-form-item>
        <el-form-item label="每人限购" prop="perUserLimit"><el-input-number v-model="form.perUserLimit" :min="1" :precision="0"/></el-form-item>
        <el-form-item label="活动时间" prop="range"><el-date-picker v-model="form.range" type="datetimerange" value-format="yyyy-MM-dd HH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" :append-to-body="true" style="width:100%"/></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
    </el-dialog>
  </div>
</template>
<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { getFlashSalePage, createFlashSale, updateFlashSale, updateFlashSaleStatus, searchFlashSaleSkuOptions } from '@/api/flashSale'
@Component({ name: 'FlashSale' })
export default class extends Vue {
  private loading=false; private saving=false; private dialog=false; private editing=false; private records:any[]=[]; private total=0
  private query:any={page:1,pageSize:10,status:undefined}
  private form:any=this.emptyForm()
  private skuOptions:any[]=[]; private skuLoading=false
  private rules:any={skuId:[{required:true,message:'请选择活动商品规格',trigger:'change'}],salePrice:[{required:true,message:'请输入限时价格',trigger:'change'}],activityStock:[{required:true,message:'请输入活动库存',trigger:'change'}],perUserLimit:[{required:true,message:'请输入每人限购数量',trigger:'change'}],range:[{required:true,message:'请选择活动时间',trigger:'change'}]}
  created(){ this.load(); this.searchSku('') }
  private emptyForm(){ return {id:null,skuId:null,salePrice:1,activityStock:1,perUserLimit:1,range:[]} }
  private money(v:any){ return Number(v || 0).toFixed(2) }
  private activityState(row:any){ if(row.status!==1)return '已停用'; const now=Date.now(); if(new Date(row.startTime).getTime()>now)return '未开始'; if(new Date(row.endTime).getTime()<=now)return '已结束'; return Number(row.remainingStock)<=0 ? '已售罄' : '进行中' }
  private activityStateType(row:any){ const state=this.activityState(row); return state==='进行中'?'success':state==='未开始'?'warning':'info' }
  private async load(){ this.loading=true; try { const res:any=await getFlashSalePage(this.query); const data=res.data.data || {}; this.records=data.records || data || []; this.total=Number(data.total || this.records.length) } catch(e) { this.$message.error('限时购数据加载失败，请确认后端服务已启动') } finally { this.loading=false } }
  private changePage(page:number){ this.query.page=page; this.load() }
  private skuLabel(item:any){ return `${item.productName} · ${item.specName || '规格'}：${item.specValue}（原价 ¥${this.money(item.price)}，库存 ${item.stock}）` }
  private async searchSku(keyword:string){ this.skuLoading=true; try { const res:any=await searchFlashSaleSkuOptions(keyword || ''); this.skuOptions=(res.data && res.data.data) || [] } catch(e) { this.skuOptions=[] } finally { this.skuLoading=false } }
  private selectSku(id:number){ const sku=this.skuOptions.find(item=>Number(item.skuId)===Number(id)); if(sku){ this.form.salePrice=Number(sku.price); this.form.activityStock=Math.max(1, Number(sku.stock || 1)) } }
  private openCreate(){ this.editing=false; this.form=this.emptyForm(); this.searchSku(''); this.dialog=true }
  private openEdit(row:any){ this.editing=true; this.form={...row, range:[row.startTime,row.endTime]}; if(!this.skuOptions.some(item=>Number(item.skuId)===Number(row.skuId))) this.skuOptions.unshift({skuId:row.skuId,productName:row.productName,specName:row.specName,specValue:row.specValue,price:row.originalPrice,stock:row.skuStock}); this.dialog=true }
  private toggle(row:any){ const next=row.status===1?0:1; this.$confirm(`确认${next?'启用':'停用'}该限时购活动？`,'提示',{type:'warning'}).then(async()=>{ await updateFlashSaleStatus(row.id,next); this.$message.success('状态已更新'); this.load() }).catch(()=>{}) }
  private save(){ (this.$refs.form as any).validate(async(valid:boolean)=>{ if(!valid)return; this.saving=true; try { const payload={...this.form,startTime:this.form.range[0],endTime:this.form.range[1]}; delete payload.range; const res:any=this.editing ? await updateFlashSale(payload) : await createFlashSale(payload); if (!res || !res.data || res.data.code !== 1) throw new Error((res && res.data && res.data.msg) || '服务端未确认保存'); const createdId=res.data.data; await this.load(); if (!this.editing && createdId && !this.records.some((item:any)=>String(item.id)===String(createdId))) throw new Error('后端未返回新活动，未确认落库'); this.$message.success('保存成功，活动已写入并显示在列表中'); this.dialog=false } catch(e) { this.$message.error((e && e.message) || '保存失败，请检查商品、规格和活动时间') } finally { this.saving=false } }) }
}
</script>
<style scoped lang="scss">
.flash-sale-page{margin:30px;background:#fff;border-radius:8px;padding:28px;min-height:calc(100vh - 120px)} .page-head{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #edf1f7;padding-bottom:20px;margin-bottom:20px}.page-head h2{margin:0 0 8px;color:#1f2937;font-size:20px}.page-head p{margin:0;color:#7a8494;font-size:13px}.filter-bar{display:flex;gap:12px;margin-bottom:20px}.pager{text-align:center;margin-top:24px}.sku-tip{margin:0 0 18px 110px;width:calc(100% - 110px)}.product-name{font-weight:600;color:#303133}.product-spec,.sku-option-detail{font-size:12px;color:#8492a6}.sku-option-detail{float:right;margin-left:16px}
</style>
