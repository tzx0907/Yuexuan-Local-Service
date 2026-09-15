package com.sky.test;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
@Disabled("Manual HTTP check; requires a separately running local application")
public class HttpClientTest {
    //测试发送GET请求
    @Test
    public void testGet() throws Exception {
        // 1. 创建 HttpClient 客户端对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 2. 创建 GET 请求
        HttpGet httpGet = new HttpGet("http://localhost:8080/admin/shop/status");
        // 3. 执行请求并获取响应
        CloseableHttpResponse response = httpClient.execute(httpGet);
        // 4. 处理响应（例如获取状态码、响应体）
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = EntityUtils.toString(response.getEntity());
        System.out.println("状态码: " + statusCode);
        System.out.println("响应体: " + responseBody);
        // 5. 关闭资源
        response.close();
        httpClient.close();
    }
    //测试发送POST请求
    @Test
    public void testPost() throws Exception {
        // 1. 创建 HttpClient 客户端对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 2. 创建 POST 请求
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");
        JSONObject json = new JSONObject();
        json.put("username", "admin");
        json.put("password", "07150907");
        StringEntity entity = new StringEntity(json.toString(),"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        // 3. 执行请求并获取响应
        CloseableHttpResponse response = httpClient.execute(httpPost);
        // 4. 处理响应（例如获取状态码、响应体）
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = EntityUtils.toString(response.getEntity());
        System.out.println("状态码: " + statusCode);
        System.out.println("响应体: " + responseBody);
        response.close();
        httpClient.close();
    }

}
