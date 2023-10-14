package net.vakror.jamesconfig.config.config.registry;

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
import java.util.List;

public abstract class RegistryConfigImpl extends Config {

    public final List<ConfigObject> objects = new ArrayList<>();

    @Override
    public void generateConfig() {
        this.resetToDefault();
        this.writeConfig();
    }

    @Override
    @NotNull
    public File getConfigDir() {
        File configDir;
        configDir = Platform.getConfigFolder().resolve(getSubPath()).toFile();
        return configDir;
    }

    @NotNull
    public File getConfigFile(String fileName) {
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

    @Override
    public void readConfig(boolean overrideCurrent) {
        readConfig(false, true);
    }

    public abstract boolean isValueAcceptable(ConfigObject value);

    public abstract boolean shouldDiscardConfigOnUnacceptableValue();

    public abstract void invalidate();

    public abstract boolean isValid();

    private File currentFile = null;
    public void readConfig(boolean overrideCurrent, boolean shouldCallAgain) {
        if (shouldReadConfig()) {
            if (!overrideCurrent) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                JamesConfigMod.LOGGER.info("Reading configs: " + this.getName());
                File[] configFiles = this.getConfigDir().listFiles(File::isFile);
                if (configFiles != null && configFiles.length != 0) {
                    for (File file : configFiles) {
                        try (FileReader reader = new FileReader(file)) {
                            Stopwatch stopwatch1 = Stopwatch.createStarted();
                            JamesConfigMod.LOGGER.info("Reading config object {} for config {}", this, file.getName());
                            JsonObject jsonObject = (JsonObject) new JsonParser().parse(reader);
                            currentFile = file;
                            List<ConfigObject> configObjects = parse(jsonObject);
                            if (configObjects != null) {
                                for (ConfigObject object : configObjects) {
                                    if (object != null) {
                                        add(object);
                                    }
                                }
                            }
                            JamesConfigMod.LOGGER.info("Finished reading config object {}, \033[0;31mTook {}\033[0;0m", file.getName(),stopwatch1);
                        } catch (IOException e) {
                            System.out.println(e.getClass());
                            e.printStackTrace();
                            JamesConfigMod.LOGGER.warn("Error with object {} in config {}, generating new", file.getName(), this);
                            this.generateConfig();
                            if (shouldCallAgain) {
                                this.objects.clear();
                                readConfig(false, false);
                            }
                        }
                    }
                    currentFile = null;
                } else {
                    this.generateConfig();
                    if (shouldCallAgain) {
                        this.objects.clear();
                        readConfig(false, false);
                    }
                    JamesConfigMod.LOGGER.warn("Config " + this.getName() + "not found, generating new");
                }
                JamesConfigMod.LOGGER.info("Finished reading config, \033[0;31mTook {}\033[0;0m", stopwatch);
            } else {
                this.generateConfig();
                if (shouldCallAgain) {
                    this.objects.clear();
                    readConfig(false, false);
                }
                JamesConfigMod.LOGGER.info("Successfully Overwrote config {}", this);
            }
            if (!this.isValid()) {
                JamesConfigMod.LOGGER.error("Config {} was found to be invalid, discarding all values", this.getName());
                this.clear();
            }
        }
    }

    @Override
    public List<ConfigObject> parse(JsonObject jsonObject) {
        if (!jsonObject.has("type") || !jsonObject.get("type").isJsonPrimitive() || !jsonObject.getAsJsonPrimitive("type").isString()) {
            JamesConfigMod.LOGGER.error("Config object {} either does not contain a type field, or the type field is not a string", currentFile.getName());
        } else {
            ConfigObject object = ConfigObject.deserializeUnknown(jsonObject);
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
    public void writeConfig() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        JamesConfigMod.LOGGER.info("Writing config {}", this);
        File cfgDir = this.getConfigDir();
        if (!cfgDir.exists()) {
            Stopwatch stopwatch1 = Stopwatch.createStarted();
            JamesConfigMod.LOGGER.info("Attempting to create config directory {}", cfgDir.getPath());
            if (!cfgDir.mkdirs()) {
                JamesConfigMod.LOGGER.error("Failed to create config directory {}, \033[0;31mTook {}\033[0;0m", cfgDir.getPath(), stopwatch1);
                return;
            }
            JamesConfigMod.LOGGER.info("Finished creating config directory {}, \033[0;31mTook {}\033[0;0m", cfgDir.getPath(), stopwatch1);
        }
        for (ConfigObject object: getAll()) {
            Stopwatch stopwatch1 = Stopwatch.createStarted();
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
                JamesConfigMod.LOGGER.info("Successfully wrote config object {} for config {}, \033[0;31mTook {}\033[0;0m", object.getName(), this, stopwatch1);
            } catch (IOException e) {
                JamesConfigMod.LOGGER.error("Failed to write config object {} for config {}, \033[0;31mTook {}\033[0;0m", object.getName(), this, stopwatch1);
                e.printStackTrace();
            }
        }
        JamesConfigMod.LOGGER.info("Finished writing config, \033[0;31mTook {}\033[0;0m", stopwatch);
    }

    @Override
    public List<JsonObject> serialize() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        JamesConfigMod.LOGGER.info("Writing config {} to network", this);
        List<JsonObject> jsonObject = new ArrayList<>();
        for (ConfigObject object : getAll()) {
            Stopwatch stopwatch1 = Stopwatch.createStarted();
            JamesConfigMod.LOGGER.info("Writing config object {} in config {} to network", object.getName(), this);
            if (!(object.serialize() instanceof JsonObject)) {
                JamesConfigMod.LOGGER.error("Config object {} in config {} does not have a json object at root, skipping write, \033[0;31mTook {}\033[0;0m", object.getName(), this, stopwatch1);
            } else {
                jsonObject.add((JsonObject) object.serialize());
                JamesConfigMod.LOGGER.info("Finished writing config object {} in config {} to network, \033[0;31mTook {}\033[0;0m", object.getName(), this, stopwatch1);
            }
        }
        JamesConfigMod.LOGGER.info("Finished writing config {} to network, \033[0;31mTook {}\033[0;0m", this, stopwatch);
        return jsonObject;
    }
}
