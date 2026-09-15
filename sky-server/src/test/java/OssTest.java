package com.sky;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;

@SpringBootTest
@Disabled("Manual OSS integration check; requires real cloud credentials and writes an object")
public class OssTest {

    @Value("${sky.alioss.endpoint}")
    private String endpoint;

    @Value("${sky.alioss.access-key-id}")
    private String accessKeyId;

    @Value("${sky.alioss.access-key-secret}")
    private String accessKeySecret;

    @Value("${sky.alioss.bucket-name}")
    private String bucketName;

    @Test
    public void testOssUpload() {
        System.out.println("Endpoint: " + endpoint);
        System.out.println("BucketName: " + bucketName);

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            String testContent = "Hello OSS!";
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    "test-oss-connection.txt",
                    new ByteArrayInputStream(testContent.getBytes()));

            PutObjectResult result = ossClient.putObject(putObjectRequest);
            System.out.println("上传成功！ETag: " + result.getETag());
        } catch (Exception e) {
            System.err.println("上传失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ossClient.shutdown();
        }
    }
}
