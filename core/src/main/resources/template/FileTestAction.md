### 单测代码规范
1. 只需要生成调用代码并打印返回结果就行，不用 assert 验证
2. 不要使用 mock 框架

单测代码文件示例如下，仅仅是示例，跟本地项目没有任何关系，纯属虚构:
```java
import com.live.ApplicationMain;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ApplicationMain.class}, properties = "spring.profiles.active=local,unittest")
public class LiveStartListenerTest {
    static {
        System.setProperty("druid.asyncInit", "true");
    }

    @Resource
    private LiveStartListener liveStartListener;

    @Test
    public void startLivingWrapper() {
        startLiving();
    }

    private void startLiving() {
        ConsumerRecord<String, String> record = new ConsumerRecord<String, String>("", 0, 0, "", "{\"bizType\":2,\"originExp\":459,\"nowLevel\":2,\"membership\":\"PLANET_USER\",\"type\":2,\"nowExp\":509,\"ruleCode\":\"\",\"uid\":233481062512750001,\"originLevel\":1,\"upLevelText\":\"\",\"extra\":\"{\\\"isNotify\\\":false,\\\"nameColor\\\":\\\"#A9EBFF\\\",\\\"barrageColor\\\":\\\"#ffffff\\\",\\\"dynamicAvatar\\\":false,\\\"superServer\\\":false,\\\"superNumber\\\":false,\\\"specialEffect\\\":false}\",\"bizId\":\"5e9466c0d4a54a23998530c8f800da15\"}");
        liveStartListener.startLiving(record);
    }
}
```

按照前文提到单测规范生成 ${GPT_className} 类的单测代码，中文作答，注释也用中文，如果内容太多，你可以分步执行，保证质量，最后汇总。
以下是提供的一些信息:
${GPT_methodInfo}
