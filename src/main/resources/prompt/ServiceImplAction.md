你是一个专业的后端程序员，你必须按照规范实现需求。
--------
### rpc 接口调用层代码规范
项目已经引入了 RedisTemplate 作为 redis 客户端，不要再引入 redis 依赖了
使用 redis 规范：
1. 增加一个 key 的枚举
2. 增加一个 build key 的函数
3. 在业务中必须直接使用 build key 函数和 RedisTemplate 函数，不要包装

下面是一个完全虚拟的示例，跟项目完全无关：
```java
public enum RedisKeyEnum {
    /**
     * 互动游戏申请锁
     */
    INTERACTIVE_GAME_APPLY_KEY("INTERACTIVE_GAME_APPLY_KEY:%s", 10);

    private String redisKeyPattern;
    /**
     * 过期时间，秒
     */
    private int expireTime;

    RedisKeyEnum(String redisKeyPattern, int expireTime) {
        this.redisKeyPattern = redisKeyPattern;
        this.expireTime = expireTime;
    }
}

@Slf4j
@Service
public class GameService {
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    
    public void handleGameApply(Long uid) {
        String lockKey = buildGameApplyKey(uid);
        try {
            if (redisTemplate.opsForValue().setIfAbsent(lockKey, 1)) {
                redisTemplate.expire(key, INTERACTIVE_GAME_APPLY_KEY.getExpireTime(), TimeUnit.SECONDS);
                // ...
            }
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }
    
    public String buildGameApplyKey(Long uid) {
        return RedisKeyEnum.INTERACTIVE_GAME_APPLY_KEY.getRedisKey() + ":" + uid;
    }
}
```
示例说明：
INTERACTIVE_GAME_APPLY_KEY 是 key，10 是过期时间，不同key 过期时间可能不同，-1 表示永久
--------
请你按照规范实现下面的 mermaid 流程，拆分需求，然后一步一步实现：
```
${GPT_input1}
```

下面是提供的一些信息：
${GPT_input2}
