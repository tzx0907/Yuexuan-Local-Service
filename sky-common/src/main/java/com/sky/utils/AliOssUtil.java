package com.sky.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 文件上传
     *
     * @param bytes
     * @param objectName
     * @return
     */
    public String upload(byte[] bytes, String objectName) {
        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 创建元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            
            // 设置ContentType
            if (objectName != null && objectName.endsWith(".png")) {
                metadata.setContentType("image/png");
            } else if (objectName != null && (objectName.endsWith(".jpg") || objectName.endsWith(".jpeg"))) {
                metadata.setContentType("image/jpeg");
            } else if (objectName != null && objectName.endsWith(".gif")) {
                metadata.setContentType("image/gif");
            } else if (objectName != null && objectName.endsWith(".bmp")) {
                metadata.setContentType("image/bmp");
            } else {
                metadata.setContentType("application/octet-stream");
            }
            
            // 创建PutObjectRequest对象
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName, 
                    objectName, 
                    new ByteArrayInputStream(bytes),
                    metadata);
            
            // 上传文件
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("OSS上传成功，ETag: {}, 文件大小: {} bytes", result.getETag(), bytes.length);
            
        } catch (OSSException oe) {
            log.error("OSS上传失败 - Error Code: {}, Error Message: {}", 
                    oe.getErrorCode(), oe.getErrorMessage());
            log.error("Request ID: {}", oe.getRequestId());
            log.error("Host ID: {}", oe.getHostId());
            throw new RuntimeException("文件上传到OSS失败: " + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("OSS客户端异常: {}", ce.getMessage());
            throw new RuntimeException("文件上传到OSS失败: " + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        // 文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder.append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件上传到: {}", stringBuilder.toString());
        return stringBuilder.toString();
    }
}
