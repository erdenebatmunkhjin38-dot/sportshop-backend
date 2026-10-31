package mn.shop.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Нэг IP хаягаас 15 минутад 5-аас олон удаа буруу нэвтрэхийг хориглоно (brute-force хамгаалалт). */
@Component
public class LoginRateLimiter {

    // Хамгийн ихдээ хэдэн удаа буруу оролдож болох
    private static final int MAX_FAILURES = 5;
    // Буруу оролдлогыг тоолох хугацаа. Энэ хугацаа өнгөрвөл тоолуур тэглэгдэнэ.
    private static final Duration WINDOW = Duration.ofMinutes(15);

    // count: буруу оролдлогын тоо, windowStart: анхны буруу оролдлого хийсэн цаг
    private record Attempts(int count, Instant windowStart) {}

    // IP хаяг бүрээр буруу оролдлогыг санах ойд хадгална.
    // ConcurrentHashMap нь олон хүсэлт зэрэг ирэхэд аюулгүй ажилладаг.
    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();

    /** Энэ IP хаяг түр хаагдсан эсэхийг шалгана. */
    public boolean isBlocked(String ip) {
        Attempts a = failures.get(ip);
        if (a == null) return false;
        // 15 минут өнгөрсөн бол бичлэгийг устгаж, дахин оролдохыг зөвшөөрнө
        if (expired(a)) {
            failures.remove(ip);
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    /** Буруу нэвтрэлт бүрт тоолуурыг нэгээр нэмнэ. */
    public void recordFailure(String ip) {
        failures.compute(ip, (k, a) -> (a == null || expired(a))
                ? new Attempts(1, Instant.now())                     // анхны буруу оролдлого
                : new Attempts(a.count() + 1, a.windowStart()));     // өмнөхөөс нэмэгдүүлнэ
    }

    /** Амжилттай нэвтэрвэл тоолуурыг тэглэнэ. */
    public void reset(String ip) {
        failures.remove(ip);
    }

    // Тоолох хугацаа (15 минут) дууссан эсэх
    private static boolean expired(Attempts a) {
        return a.windowStart().plus(WINDOW).isBefore(Instant.now());
    }
}
