import net.bplaced.abzzezz.cache.CacheUtil;
import net.bplaced.abzzezz.cache.Cacheable;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Test {

    @Cacheable(key = "key_test", expiration = 1000 * 100)
    public static int testValue = 10;
    @Cacheable(key = "key2")
    public static String testValue2 = "hello world disposed";
    @Cacheable(key = "json_test", expiration = 100)
    public static JSONObject jsonObject = new JSONObject().put("key1", "11");


    public void main() throws Exception {
        final CacheUtil cacheUtil = new CacheUtil(new File("cache"), getClass());
        cacheUtil.loadCache();

        System.out.println(cacheUtil.getFromCache("key_test").orElse("value expired for key: key_test"));
        System.out.println(cacheUtil.getFromCache("json_test").orElse("value expired for key: json_test"));

        Thread.sleep(1000);

        System.out.println(cacheUtil.getFromCache("key2").orElse("value expired for key: key2"));

        cacheUtil.flushCache();
    }

    public static void main(final String[] args) throws Exception {
        new Test().main();
    }
}
