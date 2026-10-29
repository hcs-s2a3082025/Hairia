package jp.hairia.common.validation;

import jp.hairia.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** JSONボディ（Map）から値を安全に取り出す補助。 */
public final class Body {
    private Body() {
    }

    public static String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? "" : v.toString().trim();
    }

    /** キーが無ければ null（部分更新用）。 */
    public static String opt(Map<String, Object> b, String k) {
        return b.containsKey(k) && b.get(k) != null ? b.get(k).toString() : null;
    }

    public static Boolean bool(Map<String, Object> b, String k) {
        return b.get(k) instanceof Boolean x ? x : null;
    }

    public static double num(Map<String, Object> b, String k, double d) {
        return b.get(k) instanceof Number n ? n.doubleValue() : d;
    }

    public static long id(Map<String, Object> b, String k) {
        if (b.get(k) instanceof Number n) {
            return n.longValue();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "対象が指定されていません");
    }
}
