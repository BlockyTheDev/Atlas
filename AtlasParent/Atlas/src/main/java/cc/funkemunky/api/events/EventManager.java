package cc.funkemunky.api.events;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.events.exceptions.ListenParamaterException;
import cc.funkemunky.api.events.impl.PacketReceiveEvent;
import cc.funkemunky.api.events.impl.PacketSendEvent;
import cc.funkemunky.api.tinyprotocol.api.TinyProtocolHandler;
import lombok.Getter;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Getter
public class EventManager {
    private final List<ListenerMethod> listenerMethods = new CopyOnWriteArrayList<>();
    public boolean paused = false;

    public void registerListener(Method method, AtlasListener listener, Plugin plugin) throws ListenParamaterException {
        if(method.getParameterTypes().length == 1) {
            Class<?> param = method.getParameterTypes()[0];
            if(param.getSuperclass().equals(AtlasEvent.class)) {
                Listen listen = method.getAnnotation(Listen.class);
                ListenerMethod lm = new ListenerMethod(plugin, method, listener, listen.priority());

                if(param == PacketReceiveEvent.class || param == PacketSendEvent.class) {
                    TinyProtocolHandler.legacyListeners = true;
                    Atlas.getInstance().getLogger().warning("Legacy packet listeners are being used by plugin " +
                            "" + plugin.getName() + " and therefore have been enabled. This can reduce performance," +
                            " so please update your plugin or tell the author to use the new packet listeners");
                }
                if(!listen.priority().equals(ListenerPriority.NONE)) {
                    lm.listenerPriority = listen.priority();
                }

                listenerMethods.add(lm);
                listenerMethods.sort(Comparator.comparing(mth -> mth.listenerPriority.getPriority(), Comparator.reverseOrder()));
            } else {
                throw new ListenParamaterException("Method " + method.getDeclaringClass().getName() + "#" + method.getName() + "'s paramater: " + method.getParameterTypes()[0].getName() + " is not an instanceof " + AtlasEvent.class.getSimpleName() + "!");
            }
        } else {
            throw new ListenParamaterException("Method " + method.getDeclaringClass().getName() + "#" + method.getName() + " has an invalid amount of paramters (count=" + method.getParameterTypes().length + ")!");
        }
    }

    public void clearAllRegistered() {
        listenerMethods.clear();
    }

    public void unregisterAll(Plugin plugin) {
        listenerMethods.stream()
                .filter(lm -> lm.plugin.getName().equals(plugin.getName()))
                .forEach(listenerMethods::remove);
    }

    public void unregisterListener(AtlasListener listener) {
        listenerMethods.stream().filter(lm -> lm.listener.equals(listener)).forEach(listenerMethods::remove);
    }

    public void registerListeners(AtlasListener listener, Plugin plugin) {
        Arrays.stream(listener.getClass().getMethods()).filter(method -> method.isAnnotationPresent(Listen.class)).forEach(method -> {
            try {
                registerListener(method, listener, plugin);
            } catch(ListenParamaterException e) {
                e.printStackTrace();
            }
        });
    }

    public void callEvent(AtlasEvent event) {
        if(!paused && event != null) {
            List<ListenerMethod> methods = listenerMethods.parallelStream()
                    .filter(lm -> lm.method.getParameters().get(0).equals(event.getClass()))
                    .sequential()
                    .sorted(Comparator.comparing(lm -> lm.listenerPriority.getPriority(), Comparator.reverseOrder()))
                    .collect(Collectors.toList());


            if(event instanceof Cancellable) {
                Cancellable cancellable = (Cancellable) event;
                for (ListenerMethod lm : methods) {
                    if(!cancellable.isCancelled() || !lm.ignoreCancelled) {
                        lm.method.invoke(lm.listener, cancellable);
                    }
                }
            } else {
                for(ListenerMethod lm : methods) {
                    lm.method.invoke(lm.listener, event);
                }
            }
        }
    }

    public void callEventAsync(AtlasEvent event) {
        Atlas.getInstance().getService().execute(() -> callEvent(event));
    }
}