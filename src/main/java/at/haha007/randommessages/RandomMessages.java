package at.haha007.randommessages;

import at.haha007.edencommands.CommandRegistry;
import at.haha007.edencommands.tree.LiteralCommandNode;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RandomMessages extends JavaPlugin {
    private final List<MessageList> list = new ArrayList<>();

    public void onEnable() {
        loadConfig();
        scheduleTasks();
        registerCommand();
    }

    private void registerCommand() {
        CommandRegistry registry = new CommandRegistry(this);
        LiteralCommandNode cmd = LiteralCommandNode.builder("randommessages")
                .requires(CommandRegistry.permission("randommessages.command"))
                .executor(c -> {
                    list.clear();
                    try {
                        loadConfig();
                    } catch (Throwable t) {
                        c.sender().sendMessage(Component.text("Couldn't reload config!", NamedTextColor.RED));
                        t.printStackTrace();
                        return;
                    }
                    Bukkit.getScheduler().cancelTasks(this);
                    scheduleTasks();
                    c.sender().sendMessage(Component.text("RandomMessages reloaded!", NamedTextColor.GREEN));
                }).build();
        registry.register(cmd);
    }

    private void scheduleTasks() {
        list.forEach(this::startTask);
    }

    private void startTask(MessageList messageList) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            List<String> msg = messageList.messages.getRandom();
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("randommessages." + messageList.key))
                    .forEach(p -> msg.stream()
                            .map(m -> PlaceholderAPI.setPlaceholders(p, m))
                            .map(MiniMessage.miniMessage()::deserialize)
                            .forEach(p::sendMessage));
        }, messageList.offset, messageList.delay);
    }


    private void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration cfg = getConfig();
        for (String k1 : cfg.getKeys(false)) {
            ConfigurationSection s1 = Objects.requireNonNull(cfg.getConfigurationSection(k1));
            int time = s1.getInt("time");
            int offset = s1.getInt("offset", 0);
            ConfigurationSection messages = s1.getConfigurationSection("messages");
            if (messages == null) messages = new YamlConfiguration();
            WeightedList<List<String>> weightedList = new WeightedList<>();
            for (String k2 : messages.getKeys(false)) {
                ConfigurationSection s2 = Objects.requireNonNull(messages.getConfigurationSection(k2));
                WeightedElement<List<String>> element = new WeightedElement<>(s2.getStringList("message"), s2.getDouble("weight", 1d));
                weightedList.add(element);
            }
            MessageList messageList = new MessageList(k1, weightedList, time, offset);
            list.add(messageList);
        }
    }

    private record MessageList(String key, WeightedList<List<String>> messages, int delay, int offset) {
    }

    private static class WeightedList<T> {
        private final List<WeightedElement<T>> elements = new ArrayList<>();
        private double totalWeight = 0;

        public void add(WeightedElement<T> element) {
            elements.add(new WeightedElement<>(element.element(), totalWeight));
            totalWeight += element.weight;
        }

        public T getRandom() {
            double target = Math.random() * totalWeight;
            if (elements.isEmpty()) throw new NullPointerException();
            WeightedElement<T> element = elements.get(0);
            for (WeightedElement<T> e : elements) {
                if (e.weight() > target) return element.element;
                element = e;
            }
            return element.element();

        }
    }

    private record WeightedElement<T>(T element, double weight) {
    }
}
