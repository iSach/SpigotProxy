package nl.thijsalders.spigotproxy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import nl.thijsalders.spigotproxy.netty.NettyChannelInitializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.SpigotConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

public class SpigotProxy extends JavaPlugin {

    private String channelFieldName;

    public void onLoad() {
        String version = super.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        if (Integer.parseInt(version.split("_")[1]) < 14) {
            try {
                Field field = SpigotConfig.class.getDeclaredField("lateBind");
                if (field.getBoolean(null)) { // if SpigotConfig.lateBind
                    getLogger().log(Level.SEVERE, "Please disable late-bind in the spigot config in order to make this plugin work");
                    return;
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        channelFieldName = getChannelFieldName(version);
        if (channelFieldName == null) {
            getLogger().log(Level.SEVERE, "Unknown server version " + version + ", please see if there are any updates avaible");
            return;
        } else {
            getLogger().info("Detected server version " + version);
        }
        try {
            getLogger().info("Injecting NettyHandler...");
            inject();
            getLogger().info("Injection successful!");
        } catch (Exception e) {
            getLogger().info("Injection netty handler failed!");
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void inject() throws Exception {
        Method serverGetHandle = Bukkit.getServer().getClass().getDeclaredMethod("getServer");
        Object minecraftServer = serverGetHandle.invoke(Bukkit.getServer());

        Method serverConnectionMethod = null;
        for (Method method : minecraftServer.getClass().getSuperclass().getDeclaredMethods()) {
            if (!method.getReturnType().getSimpleName().equals("ServerConnection")) {
                continue;
            }
            serverConnectionMethod = method;
            break;
        }
        Object serverConnection = serverConnectionMethod.invoke(minecraftServer);
        List<ChannelFuture> channelFutureList = ReflectionUtils.getPrivateField(serverConnection.getClass(), serverConnection, List.class, channelFieldName);

        for (ChannelFuture channelFuture : channelFutureList) {
            ChannelPipeline channelPipeline = channelFuture.channel().pipeline();
            ChannelHandler serverBootstrapAcceptor = channelPipeline.first();
            System.out.println(serverBootstrapAcceptor.getClass().getName());
            ChannelInitializer<SocketChannel> oldChildHandler = ReflectionUtils.getPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, ChannelInitializer.class, "childHandler");
            ReflectionUtils.setFinalField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler", new NettyChannelInitializer(oldChildHandler));
        }
    }

    public String getChannelFieldName(String version) {
        String name = null;
        switch (version) {
            case "v1_8_R1":
                name = "f";
                break;
            case "v1_7_R4":
                name = "e";
                break;
            default:
                name = "g";
                break;
        }
        return name;
    }
}
