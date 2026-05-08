package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

/**
 * 完整功能测试工具
 * 检查数据库结构和API功能
 */
public class FullTestUtil {

    private static final String DB_URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "ea6741b12e0bea64";
    private static final String API_BASE = "http://localhost:8080/api";

    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("开始完整功能测试");
            System.out.println("========================================\n");

            // 1. 检查数据库表结构
            checkDatabaseSchema();

            // 2. 测试API接口
            testApiEndpoints();

            System.out.println("\n========================================");
            System.out.println("测试完成");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkDatabaseSchema() throws Exception {
        System.out.println("\n--- 检查数据库表结构 ---\n");

        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        java.sql.DatabaseMetaData meta = conn.getMetaData();

        // 检查 labels 表结构
        System.out.println("检查 labels 表结构:");
        ResultSet columns = meta.getColumns(null, null, "labels", null);
        while (columns.next()) {
            String columnName = columns.getString("COLUMN_NAME");
            String columnType = columns.getString("TYPE_NAME");
            int columnSize = columns.getInt("COLUMN_SIZE");
            String isNullable = columns.getString("IS_NULLABLE");
            String remarks = columns.getString("REMARKS");

            System.out.println(String.format("  - %s: %s(%d) nullable=%s [%s]",
                columnName, columnType, columnSize, isNullable, remarks));
        }
        columns.close();

        // 检查索引
        System.out.println("\n检查 labels 表索引:");
        ResultSet indexes = meta.getIndexInfo(null, null, "labels", false, false);
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            String columnName = indexes.getString("COLUMN_NAME");
            boolean nonUnique = indexes.getBoolean("NON_UNIQUE");
            System.out.println(String.format("  - %s: %s (unique=%b)", indexName, columnName, !nonUnique));
        }
        indexes.close();

        conn.close();
        System.out.println("\n数据库结构检查完成");
    }

    private static void testApiEndpoints() throws Exception {
        System.out.println("\n--- 测试API接口 ---\n");

        // 测试1: 获取标签列表
        System.out.println("测试1: GET /api/labels");
        try {
            String response = sendRequest("GET", "/labels", null, null);
            System.out.println("成功: " + response.substring(0, Math.min(200, response.length())) + "...");
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
            System.out.println("   提示: 请确保后端服务已启动 (http://localhost:8080)");
        }

        // 测试2: 创建 structured_extraction 标签
        System.out.println("\n测试2: POST /api/labels (创建身份证号提取标签)");
        try {
            String requestBody = "{"
                + "\"name\":\"测试_身份证号提取\","
                + "\"type\":\"structured_extraction\","
                + "\"description\":\"测试从文本中提取身份证号\","
                + "\"focusColumns\":[\"警情内容\"],"
                + "\"extractFields\":[\"身份证号\"],"
                + "\"extractorConfig\":\"{\\\"extractorType\\\":\\\"id_card\\\",\\\"options\\\":{\\\"include18Digit\\\":true,\\\"include15Digit\\\":true,\\\"includeLoose\\\":false}}\""
                + "}";

            String response = sendRequest("POST", "/labels", requestBody, null);
            System.out.println("成功: " + response);
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
            if (e.getMessage().contains("Connection refused")) {
                System.out.println("   提示: 请确保后端服务已启动");
            }
        }

        // 测试3: 创建复合提取标签
        System.out.println("\n测试3: POST /api/labels (创建复合号码提取标签)");
        try {
            String requestBody = "{"
                + "\"name\":\"测试_重要号码提取\","
                + "\"type\":\"structured_extraction\","
                + "\"description\":\"测试提取身份证号、银行卡号、手机号\","
                + "\"focusColumns\":[\"警情内容\"],"
                + "\"extractFields\":[\"身份证号\",\"银行卡号\",\"手机号\"],"
                + "\"extractorConfig\":\"{\\\"extractorType\\\":\\\"composite\\\",\\\"extractors\\\":[{\\\"field\\\":\\\"身份证号\\\",\\\"extractorType\\\":\\\"id_card\\\"},{\\\"field\\\":\\\"银行卡号\\\",\\\"extractorType\\\":\\\"bank_card\\\"},{\\\"field\\\":\\\"手机号\\\",\\\"extractorType\\\":\\\"phone\\\"}]}\""
                + "}";

            String response = sendRequest("POST", "/labels", requestBody, null);
            System.out.println("成功: " + response);
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
        }

        System.out.println("\nAPI测试完成");
    }

    private static String sendRequest(String method, String path, String body, String token) throws Exception {
        URL url = new URL(API_BASE + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            br.close();
            return response.toString();
        } else {
            throw new RuntimeException("HTTP " + responseCode + ": " + conn.getResponseMessage());
        }
    }
}
