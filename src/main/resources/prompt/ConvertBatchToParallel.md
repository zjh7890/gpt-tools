# 将批量查询代码转换成并行调用。

## 转换示例
转换前代码：
```
/**
 * 批量获取用户信息。
 * @param uidList 用户 ID 列表
 * @param scenes 场景列表，用于筛选用户信息
 * @return 用户详细信息列表，如果没有找到数据则返回空列表
 */
public List<UserDetailInfoDTO> batchGetUserInfo(List<Long> uidList, List<Integer> scenes) {
    if (uidList == null || uidList.isEmpty()) {
        return new ArrayList<>();
    }

    UserDetailInfoBatchRequest request = new UserDetailInfoBatchRequest();
    request.setUidList(uidList);
    request.setScenes(UserInfoSourceEnum.getEnumsBySources(scenes));
    Response<List<UserDetailInfoDTO>> response = userService.batchGetUserInfo(request);
    if (!response.isSuccess()) {
        // 根据条件记录错误日志，仅在出现错误时记录，不记录成功调用，以避免日志过多
        log.error("UserService.batchGetUserInfo error, request={}, response={}", request, response);
        return new ArrayList<>();
    }

    return response.getResult() != null ? response.getResult() : new ArrayList<>();
}
```

转换后代码：
```
public List<UserDetailInfoDTO> batchGetUserInfo(List<Long> uidList, List<Integer> scenes) {
    if (uidList == null || uidList.isEmpty()) {
        return new ArrayList<>();
    }

    // request 分批
    // todo: 校验分批大小是否满足需求
    List<UserDetailInfoBatchRequest> requests = Lists.partition(uidList, 50).stream()
            .map(x -> {
                UserDetailInfoBatchRequest request = new UserDetailInfoBatchRequest();
                request.setUidList(x);
                request.setScenes(UserInfoSourceEnum.getEnumsBySources(scenes));
                return request;
            }).collect(Collectors.toList());

    // 并行分批请求
    return BatchUtils.parallel(
            "batchGetUserInfo",
            requests,
            userService::batchGetUserInfo,
            (req, response, collector) -> {
                if (Objects.nonNull(response.getResult())) {
                    collector.addAll(response.getResult());
                }
            },
            executorService);
}
```
## BatchUtils 工具类代码
```
import com.yupaopao.platform.common.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

@Slf4j
public class BatchUtils {

    public static <R, D, O> List<O> parallel(String name,
                                             List<R> partReqs,
                                             Function<R, Response<D>> supplier,
                                             TriConsumer<R, Response<D>, ConcurrentLinkedQueue<O>> consumer,
                                             ExecutorService executorService) {
        if (CollectionUtils.isEmpty(partReqs)) {
            return new ArrayList<>();
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ConcurrentLinkedQueue<O> queue = new ConcurrentLinkedQueue<>();

        for (R partReq : partReqs) {
            CompletableFuture<Void> future = CompletableFuture
                    .supplyAsync(() -> supplier.apply(partReq), executorService)
                    .thenAccept(response -> {
                        if (!response.isSuccess()) {
                            log.error("BatchUtils {} 调用错误, req: {}, res: {}", name, partReq, response);
                            return;
                        }
                        consumer.accept(partReq, response, queue);
                    });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allFutures.join(); // 等待所有异步操作完成
        } catch (Exception e) {
            log.error("BatchUtils.parallel调用失败, reqs: {}", partReqs, e);
        }

        return new ArrayList<>(queue);
    }

    public static <R, D, K, O> Map<K, O> parallelMap(String name,
                                                     List<R> partReqs,
                                                     Function<R, Response<D>> supplier,
                                                     TriConsumer<R, Response<D>, ConcurrentHashMap<K, O>> consumer,
                                                     ExecutorService executorService) {
        if (CollectionUtils.isEmpty(partReqs)) {
            return new HashMap<>();
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ConcurrentHashMap<K, O> map = new ConcurrentHashMap<>();

        for (R partReq : partReqs) {
            CompletableFuture<Void> future = CompletableFuture
                    .supplyAsync(() -> supplier.apply(partReq), executorService)
                    .thenAccept(response -> {
                        if (!response.isSuccess()) {
                            log.error("BatchUtils {} 调用错误, req: {}, res: {}", name, partReq, response);
                            return;
                        }
                        consumer.accept(partReq, response, map);
                    });
            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allFutures.join(); // 等待所有异步操作完成
        } catch (Exception e) {
            log.error("BatchUtils.parallelMap调用失败, reqs: {}", partReqs, e);
        }

        return new HashMap<>(map);
    }

}
```

转换过程中需要遵守一下规范：
1. 参数，返回值必须保持一致
2. 参数必须判空
3. 注意使用分批之后的参数 chunk调用接口，不要用原 参数
4. 在 chunk 的地方留一个 todo 注释让我进行确认分批参数大小，像示例代码中那样，这个参数过大会导致调用失败，问题很严重，我可能会忘了校验，所以你必须留一个 todo，你必须留一个 todo，你必须留一个 todo，你必须留一个 todo
5. 并行调用直接必须使用 BatchUtils.parallel 工具，这是一个封装好的并行调用工具
6. 代码风格尽量参考示例
7. 你可以认为 executorService 已经上类成员变量中定义好了
8. response.getResult() 注意判空

按照前文提到的规范生成 ${GPT_methodName} 方法的并行调用调用代码，中文作答，注释也用中文。
以下是提供的一些信息:
${GPT_completeSignature}
```
${GPT_simplifyClassText}
```
${GPT_methodInfo}
        