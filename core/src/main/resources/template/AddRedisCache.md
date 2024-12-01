下面是一个缓存的示例和规范说明。
示例：
```
@Resource
    private RedisTemplate<String, String> redisTemplate;

    public static final String NULL = "null";
 
    public static String buildMultiGameConfigKey(Long liveRoomId) {
        return String.format("multi:game:config:%s", liveRoomId);
    }

    public MultiGameConfigDO findMultiGameConfig(Long liveRoomId) {
        String redisKey = buildMultiGameConfigKey(liveRoomId);
        Object cachedObject = redisTemplate.opsForValue().get(redisKey);

        if (cachedObject == null) {
            // 从数据库获取配置
            MultiGameConfigDO result = queryByLiveRoomId(liveRoomId);

            int expireSeconds = 3;
            if (result == null) {
                // 如果结果为空，将字符串 "null" 缓存
                redisTemplate.opsForValue().set(redisKey, NULL, expireSeconds, TimeUnit.SECONDS);
                return null;
            } else {
                // 将结果缓存
                redisTemplate.opsForValue().set(redisKey, JSONObject.toJSONString(result), expireSeconds, TimeUnit.SECONDS);
                return result;
            }
        } else {
            if (NULL.equalsIgnoreCase(cachedObject.toString())) {
                // 检测到字符串 "null"，返回 null
                return null;
            } else {
                return JSON.parseObject(cachedObject.toString(), MultiGameConfigDO.class);
            }
        }
    }
```
示例规范说明：
1. 给出的示例会缓存空值，如果需求不需要缓存空值，你需要做相应调整
2. 如果要缓存空值，定义一个空值常量，值就用 "null"，比较的时候用 equalsIgnoreCase
3. 不要有魔法数字，如 expireSeconds。


请你严格参考示例和规范给方法 ${GPT_methodName} 增加缓存的逻辑，方法如下：
${GPT_methodText}
