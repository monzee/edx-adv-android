/*
 * This file is a part of the Lesson 1 project.
 */

package em.zed.androidchat.backend.firebase;

public final class Schema {

    /* root :: {
     *   users :: Map EmailKey {
     *     email :: str
     *     online :: bool
     *     contacts :: Map EmailKey bool
     *   }
     *   chats :: Map PairKey (Map PushId {
     *     msg :: str
     *     sender :: EmailKey
     *     sentByMe :: bool
     *   })
     * }
     * where
     *   EmailKey = email with illegal chars replaced by underscore
     *   PairKey = 2 EmailKeys connected by 3 underscores
     *   PushId = autogenerated string
     */

    public static final String USERS = "users";
    public static final String CHATS = "chats";

    static final String CONTACTS = "contacts";
    static final String EMAIL = "email";
    static final String ONLINE = "online";
    static final String MESSAGE = "msg";
    static final String SENDER = "sender";
    static final String SENT_BY_ME = "sentByMe";  // i don't get why this is persisted

    static String legalize(String key) {
        return key.replaceAll("[.$#\\[\\]]", "_");
    }

    static String illegalize(String key) {
        return key.replaceAll("_", ".");
    }

    static String pathTo(String first, String... segments) {
        StringBuilder sb = new StringBuilder(legalize(first));
        for (String s : segments) {
            sb.append('/').append(legalize(s));
        }
        return sb.toString();
    }

    private Schema() {}

}
