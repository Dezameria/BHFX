package net.deizyn.bhfx.api.client;

/** Reference to an effect started on this client only. {@link #NONE} when nothing was started. */
public record LocalFxHandle(int id) {
    public static final LocalFxHandle NONE = new LocalFxHandle(0);

    public boolean isNone() {
        return id == 0;
    }
}
