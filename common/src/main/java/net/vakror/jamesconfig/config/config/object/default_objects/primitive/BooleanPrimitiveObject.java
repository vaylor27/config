package net.vakror.jamesconfig.config.config.object.default_objects.primitive;

import net.vakror.jamesconfig.config.config.object.ConfigObject;

/**
 * Representation of a {@link Boolean} primitive as a {@link ConfigObject}
 */
public class BooleanPrimitiveObject extends PrimitiveObject<Boolean> {

    /**
     * @param content the value of the primitive
     * @param name the name of the primitive
     */
    public BooleanPrimitiveObject(Boolean content, String name) {
        super(content, name);
    }

    /**
     * a constructor which sets the name to null
     * @param content the value of the primitive
     */
    public BooleanPrimitiveObject(Boolean content) {
        super(content);
    }
}
