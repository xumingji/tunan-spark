package com.tunan.doris.http;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import scala.util.Random;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
/**
 * This class is a java demo for dorisdb stream load
 *
 * The pom.xml dependency:
 *
 *         <dependency>
 *             <groupId>org.apache.httpcomponents</groupId>
 *             <artifactId>httpclient</artifactId>
 *             <version>4.5.3</version>
 *         </dependency>
 *
 * How to use:
 *
 * 1 create a table in dorisdb with any mysql client
 *
 * CREATE TABLE `stream_test` (
 *   `id` bigint(20) COMMENT "",
 *   `id2` bigint(20) COMMENT "",
 *   `username` varchar(32) COMMENT ""
 * ) ENGINE=OLAP
 * DUPLICATE KEY(`id`)
 * DISTRIBUTED BY HASH(`id`) BUCKETS 20;
 *
 *
 * 2 change the Dorisdb cluster, db, user config in this class
 *
 * 3 run this class, you should see the following output:
 *
 * {
 *     "TxnId": 27,
 *     "Label": "39c25a5c-7000-496e-a98e-348a264c81de",
 *     "Status": "Success",
 *     "Message": "OK",
 *     "NumberTotalRows": 10,
 *     "NumberLoadedRows": 10,
 *     "NumberFilteredRows": 0,
 *     "NumberUnselectedRows": 0,
 *     "LoadBytes": 50,
 *     "LoadTimeMs": 151
 * }
 *
 * Attention:
 *
 * 1 wrong dependency version(such as 4.4) of httpclient may cause shaded.org.apache.http.ProtocolException
 *   Caused by: shaded.org.apache.http.ProtocolException: Content-Length header already present
 *     at shaded.org.apache.http.protocol.RequestContent.process(RequestContent.java:96)
 *     at shaded.org.apache.http.protocol.ImmutableHttpProcessor.process(ImmutableHttpProcessor.java:132)
 *     at shaded.org.apache.http.impl.execchain.ProtocolExec.execute(ProtocolExec.java:182)
 *     at shaded.org.apache.http.impl.execchain.RetryExec.execute(RetryExec.java:88)
 *     at shaded.org.apache.http.impl.execchain.RedirectExec.execute(RedirectExec.java:110)
 *     at shaded.org.apache.http.impl.client.InternalHttpClient.doExecute(InternalHttpClient.java:184)
 *
 *2 run this class more than once, the status code for http response is still ok, and you will see
 *  the following output:
 *
 * {
 *     "TxnId": -1,
 *     "Label": "39c25a5c-7000-496e-a98e-348a264c81de",
 *     "Status": "Label Already Exists",
 *     "ExistingJobStatus": "FINISHED",
 *     "Message": "Label [39c25a5c-7000-496e-a98e-348a264c81de"] has already been used.",
 *     "NumberTotalRows": 0,
 *     "NumberLoadedRows": 0,
 *     "NumberFilteredRows": 0,
 *     "NumberUnselectedRows": 0,
 *     "LoadBytes": 0,
 *     "LoadTimeMs": 0
 * }
 * 3 when the response statusCode is 200, that doesn't mean your stream load is ok, there may be still
 *   some stream problem unless you see the output with 'ok' message
 */
public class DorisdbStreamLoad {
    private final static String DORISDB_HOST = "aliyun";
    private final static String DORISDB_DB = "example_db";
    private final static String DORISDB_TABLE = "stream_test";
    private final static String DORISDB_USER = "doris";
    private final static String DORISDB_PASSWORD = "doris";
    private final static int DORISDB_HTTP_PORT = 8096;

    private void sendData(String content) throws Exception {
        final String loadUrl = String.format("http://%s:%s/api/%s/%s/_stream_load",
                DORISDB_HOST,
                DORISDB_HTTP_PORT,
                DORISDB_DB,
                DORISDB_TABLE);

        final HttpClientBuilder httpClientBuilder = HttpClients
                .custom()
                .setRedirectStrategy(new DefaultRedirectStrategy() {
                    @Override
                    protected boolean isRedirectable(String method) {
                        return true;
                    }
                });

        try (CloseableHttpClient client = httpClientBuilder.build()) {
            HttpPut put = new HttpPut(loadUrl);
            StringEntity entity = new StringEntity(content, "UTF-8");
            put.setHeader(HttpHeaders.EXPECT, "100-continue");
            put.setHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(DORISDB_USER, DORISDB_PASSWORD));
            // 使用label可以确保最多一次语义
            put.setHeader("label", new Random().nextLong()+"");
            put.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(put)) {
                String loadResult = "";
                if (response.getEntity() != null) {
                    loadResult = EntityUtils.toString(response.getEntity());
                }
                final int statusCode = response.getStatusLine().getStatusCode();
                // 状态码 200 只是表明dorisdb服务是ok的，而不是流加载
                // 应该看到输出内容，以确定流加载是否成功
                if (statusCode != 200) {
                    throw new IOException(
                            String.format("Stream load failed, statusCode=%s load result=%s", statusCode, loadResult));
                }

                System.out.println(loadResult);
            }
        }
    }

    private String basicAuthHeader(String username, String password) {
        final String tobeEncode = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(tobeEncode.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encoded);
    }

    public static void main(String[] args) throws Exception {
        int id1 = 1;
        int id2 = 10;
        String id3 = "Simon";
        int rowNumber = 500000;

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < rowNumber; i++) {

            String oneRow = i + "\t" + id2 + "\t" + id3 + "\n";

            stringBuilder.append(oneRow);
        }
        
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);

        String loadData = stringBuilder.toString();
        DorisdbStreamLoad dorisdbStreamLoad = new DorisdbStreamLoad();
        dorisdbStreamLoad.sendData(loadData);
    }
}