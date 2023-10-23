package moe.yushi.yggdrasil_mock.utils.secure;

public class PermissionUtils {
    public static boolean isWebAdmin(int permission) {
        return ((permission >> 3) & 1) == 1;
    }

    public static boolean isServerAdmin(int permission) {
        return ((permission >> 2) & 1) == 1;
    }

    public static boolean isInternalMember(int permission) {
        return ((permission >> 1) & 1) == 1;
    }

    public static boolean isExternalMember(int permission) {
        return (permission & 1) == 1;
    }
}
