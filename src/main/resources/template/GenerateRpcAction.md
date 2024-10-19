### rpc 接口调用层代码规范
desc: 调用外部 dubbo 接口的层级
rpc 层规范：
1. 函数返回值不应该返回 com.platform.common.dto.Response，而是返回 Response<T> 里的 T
2. 如果 rpc 的函数返回值是集合类型，List 或 Map 或 Set 等，你需要对 result 进行判空，而且你应该返回空集合而不是 null，如 List 你应该返回  new ArrayList<>(), Map 和 Set 同理
3. 如果 rpc 的返回值是对象类型，当你想要返回空对象时，你不要使用 Optional 类，也不要使用 new 一个空对象，可以返回 null, 调用方会判空。
4. 你可以认为调用 DubboReference 时不会出现异常，因为框架已经实现了 Filter，不要再捕获异常，代码冗余
5. 如果调用是成功的，不要打印日志，因为日志太多会拖垮服务,如果response.isSuccess()为false,打印错误日志
6. 如果 Request 里面只有简单参数，生成 RPC 方法的时候必须拆分开，如 fooRpc(int a, int b, int c)

源文件示例如下，仅仅是示例，只给你用作格式参考，跟本地项目没有任何关系，纯属虚构:
```java
import com.live.tag.api.LiveAnchorTagRemoteService;
import com.live.tag.dto.AnchorStartLiveAuthDTO;
import com.platform.common.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 直播权限校验 RPC 服务
 */
@Component
@Slf4j
public class LiveAnchorAuthRPC {

    @DubboReference
    private LiveAnchorTagRemoteService liveAnchorTagRemoteService;

    /**
     * 批量检查直播权限
     * @param anchorIds 主播ID列表
     * @return 主播ID与其对应的直播权限信息的映射
     */
    public Map<Long, AnchorStartLiveAuthDTO> batchCheckLiveAuth(List<Long> anchorIds) {
        if (anchorIds == null || anchorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Response<Map<Long, AnchorStartLiveAuthDTO>> response = liveAnchorTagRemoteService.batchCheckLiveAuth(anchorIds);
        if (!response.isSuccess()) {
            log.error("LiveAnchorTagRemoteService.batchCheckLiveAuth error, request={}, response={}", anchorIds, response);
            return Collections.emptyMap();
        }

        Map<Long, AnchorStartLiveAuthDTO> result = response.getResult();
        if (result == null || result.isEmpty()) {
            return Collections.emptyMap();
        }

        return result;
    }
}
```

按照前文提到的Rpc规范生成 ${GPT_methodName} 方法的 Rpc 调用代码，中文作答，注释也用中文。
以下是提供的一些信息:
${GPT_completeSignature}
```
${GPT_simplifyClassText}
```
${GPT_methodInfo}
        