package jp.hairia.common.validation;

import jp.hairia.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** JSONボディ（Map）から値を安全に取り出す補助。 */
public final class Body {
    private static final java.util.regex.Pattern IMG = java.util.regex.Pattern.compile("^data:image/(jpeg|png|gif|webp);base64,[A-Za-z0-9+/=]++$");

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

    /** 画像のデータURIだけを許可する（それ以外は400）。空なら null。 */
    public static String imageUri(String v, int maxLen) {
        if (v == null || v.isBlank()) {
            return null;
        }
        if (v.length() > maxLen || !IMG.matcher(v).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "画像の形式が正しくないか大きすぎます");
        }
        return v;
    }
}
