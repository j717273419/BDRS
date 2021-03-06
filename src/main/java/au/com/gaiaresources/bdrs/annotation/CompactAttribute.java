package au.com.gaiaresources.bdrs.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Used to indicate the name of a member is included during
 * serialization / flattening in PersistentImpl.flatten()
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CompactAttribute {
}
