你是一个专业的后端程序员，你必须按照规范实现需求。
--------
### redis 规范
项目已经引入了 RedisTemplate 作为 redis 客户端，不要再引入 redis 依赖了
使用 redis 规范：
1. 定义 key 和 expire 时间的常量
2. 增加一个 build key 的函数
3. 在业务中必须直接使用 build key 函数和 RedisTemplate 函数，不要包装

下面是一个完全虚拟的redis 使用示例，跟项目完全无关：
```java
@Slf4j
@Service
public class GameService {
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    public static final String INTERACTIVE_GAME_APPLY_KEY = "INTERACTIVE_GAME_APPLY_KEY:%s";

    public static final int INTERACTIVE_GAME_APPLY_EXPIRE = 10;

    public void handleGameApply(Long uid) {
        String lockKey = buildGameApplyKey(uid);
        try {
            if (redisTemplate.opsForValue().setIfAbsent(lockKey, 1)) {
                redisTemplate.expire(key, INTERACTIVE_GAME_APPLY_EXPIRE, TimeUnit.SECONDS);
                // ...
            }
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    public String buildGameApplyKey(Long uid) {
        return String.format(INTERACTIVE_GAME_APPLY_KEY, uid);
    }
}
```

### 枚举定义示例
```
public enum BehaviorRecordEnum {

    /**
     * 聊天室首次行为记录
     */
    Press_Talk(1, "长按发言内容"),
    Pendant_Guide(2, "装扮引导"),
    Dispatch_RoomTag_Enter(6, "访问派单房间标签厅"),
    Voice_RoomTag_Enter(7, "访问声优房间标签厅"),
    FM_Debate_Enter(12, "访问辩论房标签厅"),

    ;

    private int code;
    private String desc;

    BehaviorRecordEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
```
--------
### 下面是提供的一些信息
${GPT_input2}
--------
请你按照规范实现下面的 mermaid 流程，拆分需求，然后一步一步实现：
${GPT_input1}
