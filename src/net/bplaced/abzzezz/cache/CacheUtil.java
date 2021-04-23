package net.bplaced.abzzezz.cache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CacheUtil extends HashMap<String, Object> {

    private final File cache;

    public final File cacheConfig;

    private final Map<String, Cache> expirationKeyMap = new HashMap<>();

    private final JSONObject cacheConfigMap;

    private final Class<?> c;

    public CacheUtil(final File cacheDirectory, final Class<?> c) {
        this.c = c;
        if (!cacheDirectory.exists())
            cacheDirectory.mkdirs();
        this.cache = new File(cacheDirectory, "cache.json");
        this.cacheConfig = new File(cacheDirectory, "cache_config.json");
        this.cacheConfigMap = new JSONObject(Util.readFile(cacheConfig).map(JSONObject::new).orElse(new JSONObject()));
    }

    /**
     * Initialises the cache.
     *
     * @return if cache has been initialised
     */
    private boolean initialiseCache() {
        if (!cache.exists()) {
            try {
                cache.createNewFile();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else return true;
    }

    /**
     * Load the cache from disk
     */
    public void loadCache() {
        if (!this.initialiseCache())
            return;

        final JSONArray fileArray = Util.readFile(cache).map(JSONArray::new).orElse(new JSONArray());
        for (int i = 0; i < fileArray.length(); i++) {
            final JSONObject jsonAt = fileArray.getJSONObject(i);
            cacheObject(
                    jsonAt.getString("key"),
                    jsonAt.get("value"),
                    new Cache(
                            jsonAt.getLong("insert"),
                            jsonAt.getLong("exp")
                    )
            );
        }
        this.reflect();
    }

    /**
     * Writes the cache to disk
     * Creates a new json array with all cached objects, their key and expiration time
     */
    public void flushCache() {
        if (!this.initialiseCache())
            return;
        this.checkCache();

        final JSONArray jsonArray = new JSONArray();

        keySet().forEach(s -> {
            final Cache cache = expirationKeyMap.get(s);
            final JSONObject cacheObject = new JSONObject()
                    .put("key", s)
                    .put("value", this.get(s))
                    .put("insert", cache.getInsertTime())
                    .put("exp", cache.getExpiration());
            jsonArray.put(cacheObject);
        });
        Util.writeFile(cache, jsonArray.toString());
    }

    /**
     * Searches for all Cachable reflections
     */
    private void reflect() {
        for (final Field field : c.getFields()) {
            if (field.isAnnotationPresent(Cacheable.class) && field.canAccess(null)) {
                final Cacheable cacheable = field.getAnnotation(Cacheable.class);
                try {
                    cacheObject(cacheable.key().isEmpty() ? field.getName() : cacheable.key(), field.get(field.getName()), cacheable.expiration());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Goes through all keys and removes every invalid / expired cache
     */
    public void checkCache() {
        this.keySet().stream()
                .filter(key -> Optional.ofNullable(expirationKeyMap.get(key))
                        .map(cache -> System.currentTimeMillis() - cache.getInsertTime() >= cache.getExpiration())
                        .orElse(true))
                .collect(Collectors.toList())
                .forEach(key -> {
                    expirationKeyMap.remove(key);
                    this.remove(key);
                });
    }

    /**
     * Loops through all keys and checks their expiration
     * if the key is expired, the corresponding cache and map entry is deleted
     */
    public Optional<Object> getOptional(final String key) {
        final Optional<Cache> optional = Optional.ofNullable(expirationKeyMap.get(key));
        if (optional.isPresent()) {
            final Cache cache = optional.get();
            if (cache.getInsertTime() != -1 && System.currentTimeMillis() - cache.getInsertTime() >= cache.getExpiration()) {
                this.remove(key);
                expirationKeyMap.remove(key);
                return Optional.empty();
            }
            return Optional.ofNullable(this.get(key));
        }
        return Optional.empty();
    }

    /**
     * Checks if a key is cached
     *
     * @param key key to check
     * @return if objects with given key has already been cached
     */
    public boolean cached(final String key) {
        return this.containsKey(key);
    }

    /**
     * Caches an object with a key and infinite expiration date
     *
     * @param key key
     * @param o   objects to cache
     */
    public void cacheObject(final String key, final Object o) {
        this.put(key, o);
        //No expiration
        this.expirationKeyMap.put(key, new Cache(System.currentTimeMillis(), -1L));
    }

    /**
     * Caches an object with a key and given expiration date
     *
     * @param key        key
     * @param o          objects to cache
     * @param expiration expiration date
     */
    public void cacheObject(final String key, final Object o, final long expiration) {
        this.put(key, o);
        this.expirationKeyMap.put(key, new Cache(System.currentTimeMillis(), expiration));
    }

    /**
     * Caches an object with a key and given expiration date
     *
     * @param key   key
     * @param o     objects to cache
     * @param cache expiration cache
     */
    public void cacheObject(final String key, final Object o, final Cache cache) {
        this.put(key, o);
        this.expirationKeyMap.put(key, cache);
    }

    /**
     * Returns the given object from cache. Warped with an optional for ease of use
     *
     * @param key key to get cached object from
     * @return the corresponding object or an empty optional
     */
    public Optional<Object> getFromCache(final String key) {
        return getOptional(key);
    }

    /**
     * Returns the object or caches the default value and returns it
     *
     * @param key           key to get cache from and store it if necessary
     * @param defaultReturn the default return value if no cache is given
     * @param expiration    expiration deadline
     * @return the object or caches the default value and returns it
     */
    public Object getFromCacheOrCache(final String key, final Object defaultReturn, final long expiration) {
        return getOptional(key).orElseGet(() -> {
            this.cacheObject(key, defaultReturn, expiration);
            return defaultReturn;
        });
    }

    /**
     * Returns the object or caches the default value and returns it
     *
     * @param key           key to get cache from and store it if necessary
     * @param defaultReturn the default return value if no cache is given
     * @return the object or caches the default value and returns it
     */
    public Object getFromCacheOrCache(final String key, final Object defaultReturn) {
        return getOptional(key).orElseGet(() -> {
            this.cacheObject(key, defaultReturn);
            return defaultReturn;
        });
    }

    /**
     * Returns the whole cache.
     *
     * @return this
     */
    public Map<String, Object> get() {
        return this;
    }

    public JSONObject getCacheConfigMap() {
        return cacheConfigMap;
    }

    public long getFromConfig(final String key) {
        return cacheConfigMap.getLong("key");
    }

    private static class Util {

        public static Optional<String> readFile(final File file) {
            if (!file.exists()) return Optional.empty();
            final StringBuilder builder = new StringBuilder();
            try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    builder.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Optional.of(builder.toString());
        }

        public static void writeFile(final File file, final String string) {
            try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.write(string.getBytes(StandardCharsets.UTF_8));
                fileOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
