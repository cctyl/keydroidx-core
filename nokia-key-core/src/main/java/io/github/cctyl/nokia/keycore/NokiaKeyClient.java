package io.github.cctyl.nokia.keycore;

import android.content.Context;
import androidx.annotation.NonNull;
import io.github.cctyl.nokia.keycore.model.NokiaKeyBinding;

/**
 * 兼容旧版本调用入口，直接代理到 {@link NokiaClient}。
 */
public class NokiaKeyClient {

    public interface OnKeyBindingChangedListener {
        default void onKeyBindingChanged(@NonNull NokiaKeyBinding binding, @NonNull NokiaClient.ConfigSource source) {}
        default void onKeyBindingChanged(boolean isFromDesktop) {}
    }

    public static NokiaClient get(@NonNull Context context) {
        return NokiaClient.get(context);
    }
}
