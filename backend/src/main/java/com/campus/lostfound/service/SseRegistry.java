package com.campus.lostfound.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 长连接注册表：按用户保存连接，新通知实时推送
 */
@Component
public class SseRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        return emitter;
    }

    public void push(Long userId, Object data) {
        push(userId, "notification", data);
    }

    public void push(Long userId, String event, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            // SseEmitter 已关闭/完成时 send 会抛 IllegalStateException（非 IOException），
            // 此处兜底捕获并移除失效连接，避免推送异常影响主流程（消息已落库）
            emitters.remove(userId);
        }
    }
}
