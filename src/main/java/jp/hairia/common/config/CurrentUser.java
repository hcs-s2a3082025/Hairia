package jp.hairia.common.config;

/** ログイン中の利用者。profileId は美容師なら hairdresser_id、お客様なら customer_id。 */
public record CurrentUser(long userId, String type, long profileId) {
    public boolean isHairdresser() {
        return "HAIRDRESSER".equals(type);
    }
}
