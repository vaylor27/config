package net.vakror.jamesconfig.config.config.registry.multi_object;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.vakror.jamesconfig.JamesConfigMod;
import net.vakror.jamesconfig.config.config.Config;
import net.vakror.jamesconfig.config.config.object.ConfigObject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MultiObjectRegistryConfigImpl extends Config {

    public final List<ConfigObject> objects = new ArrayList<>();

    @Override
    public final void generateDefaultConfig() {
        this.resetToDefault();
        this.writeConfig();
    }

    @Override
    @NotNull
    public final File getConfigDir() {
        File configDir;
        configDir = Platform.getConfigFolder().resolve(getSubPath()).toFile();
        return configDir;
    }

    @NotNull
    public final File getConfigFile(String fileName) {
        File configDir;
        configDir = Platform.getConfigFolder().resolve(getSubPath() + "/" + fileName + ".json").toFile();
        return configDir;
    }

    @Override
    public abstract String getSubPath();

    @Override
    public abstract ResourceLocation getName();

    @Override
    public String toString() {
        return this.getName().toString();
    }

    public Stopwatch loadTime;
    public Map<String, Stopwatch> parseTime = new HashMap<>();

    @Override
    public final void readConfig(boolean overrideCurrent) {
        Stopwatch stopwatch1 = Stopwatch.createStarted();
        if (shouldReadConfig()) {
            if (!overrideCurrent) {
                JamesConfigMod.LOGGER.info("Reading configs: " + this.getName());
                File[] configFiles = this.getConfigDir().listFiles(File::isFile);
                if (configFiles != null && configFiles.length != 0) {
                    for (File file : configFiles) {
                        try (FileReader reader = new FileReader(file)) {
                            Stopwatch stopwatch = Stopwatch.createStarted();
                            JamesConfigMod.LOGGER.info("Reading config object {} for config {}", this, file.getName());
                            JsonObject jsonObject = (JsonObject) new JsonParser().parse(reader); /*we are doing this the deprecated way for 1.17.1 compat*/
                            currentFile = file;
                            List<ConfigObject> configObjects = parse(jsonObject);
                            if (configObjects != null) {
                                for (ConfigObject object : configObjects) {
                                    if (object != null) {
                                        add(object);
                                    }
                                }
                            }
                            stopwatch.stop();
                            parseTime.put(file.getName(), stopwatch);
                            JamesConfigMod.LOGGER.info("Finished reading config object {}", file.getName());
                        } catch (IOException e) {
                            System.out.println(e.getClass());
                            e.printStackTrace();
                            JamesConfigMod.LOGGER.warn("Error with object {} in config {}, generating new", file.getName(), this);
                            this.generateDefaultConfig();
                        }
                    }
                    currentFile = null;
                } else {
                    this.generateDefaultConfig();
                    JamesConfigMod.LOGGER.warn("Config " + this.getName() + "not found, generating new");
                }
                JamesConfigMod.LOGGER.info("Finished reading config");
            } else {
                this.generateDefaultConfig();
                JamesConfigMod.LOGGER.info("Successfully Overwrote config {}", this);
            }
            if (!this.isValid()) {
                JamesConfigMod.LOGGER.error("Config {} was found to be invalid, discarding all values", this.getName());
                this.clear();
            }
        }
        stopwatch1.stop();
        loadTime = stopwatch1;
    }

    public abstract boolean isValueAcceptable(ConfigObject value);

    public abstract boolean shouldDiscardConfigOnUnacceptableValue();

    public abstract void invalidate();

    public abstract boolean isValid();

    private File currentFile = null;

    @Override
    public final List<ConfigObject> parse(JsonObject jsonObject) {
        if (!jsonObject.has("type") || !jsonObject.get("type").isJsonPrimitive() || !jsonObject.getAsJsonPrimitive("type").isString()) {
            JamesConfigMod.LOGGER.error("Config object {} either does not contain a type field, or the type field is not a string", currentFile.getName());
        } else {
            ConfigObject object = ConfigObject.deserializeUnknown(jsonObject, this.toString());
            if (object != null) {
                if (shouldAddObject(object)) {
                    if (this.isValueAcceptable(object)) {
                        return List.of(object);
                    } else {
                        if (shouldDiscardConfigOnUnacceptableValue()) {
                            JamesConfigMod.LOGGER.error("Discarding config because value {} is unacceptable", object.getName());
                            this.invalidate();
                        } else {
                            JamesConfigMod.LOGGER.error("Discarding unacceptable value {} in config {}", object.getName(), getName());
                            this.discardValue(object);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<ConfigObject> getAll() {
        return objects;
    }

    public abstract void discardValue(ConfigObject object);

    public abstract boolean shouldAddObject(ConfigObject object);

    protected abstract void resetToDefault();

    @Override
    public final void writeConfig() {
        JamesConfigMod.LOGGER.info("Writing config {}", this);
        File cfgDir = this.getConfigDir();
        if (!cfgDir.exists()) {
            if (!cfgDir.mkdirs()) {
                JamesConfigMod.LOGGER.error("Failed to create config directory {}", cfgDir.getPath());
                return;
            }
            JamesConfigMod.LOGGER.info("Finished creating config directory {}", cfgDir.getPath());
        }
        for (ConfigObject object: getAll()) {
            JamesConfigMod.LOGGER.info("Attempting to write config object {} for config {}", object.getName(), this);
            try(
                    FileWriter writer = new FileWriter(getConfigFile(object.getName().replaceAll(" ", "_").replaceAll("[^A-Za-z0-9_]", "").toLowerCase()));
                    JsonWriter jsonWriter = new JsonWriter(writer)) {
                jsonWriter.setIndent("    ");
                jsonWriter.setSerializeNulls(true);
                jsonWriter.setLenient(true);
                if (!(object.serialize() instanceof JsonObject)) {
                    JamesConfigMod.LOGGER.error("Config object {} in config {} does not have a json object at root, skipping write", object.getName(), this);
                } else {
                    Streams.write(object.serialize(), jsonWriter);
                    writer.flush();
                }
                JamesConfigMod.LOGGER.info("Successfully wrote config object {} for config {}", object.getName(), this);
            } catch (IOException e) {
                JamesConfigMod.LOGGER.error("Failed to write config object {} for config {}", object.getName(), this);
                e.printStackTrace();
            }
        }
        JamesConfigMod.LOGGER.info("Finished writing config");
    }

    @Override
    public final List<JsonObject> serialize() {
        JamesConfigMod.LOGGER.info("Writing config {} to network", this);
        List<JsonObject> jsonObject = new ArrayList<>();
        for (ConfigObject object : getAll()) {
            JamesConfigMod.LOGGER.info("Writing config object {} in config {} to network", object.getName(), this);
            if (!(object.serialize() instanceof JsonObject)) {
                JamesConfigMod.LOGGER.error("Config object {} in config {} does not have a json object at root, skipping write", object.getName(), this);
            } else {
                jsonObject.add((JsonObject) object.serialize());
                JamesConfigMod.LOGGER.info("Finished writing config object {} in config {} to network", object.getName(), this);
            }
        }
        JamesConfigMod.LOGGER.info("Finished writing config {} to network", this);
        return jsonObject;
    }
}
