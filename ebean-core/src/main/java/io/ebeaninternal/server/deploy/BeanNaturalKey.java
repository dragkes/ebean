package io.ebeaninternal.server.deploy;

import io.ebeaninternal.server.query.STreeProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Natural key for a bean type.
 */
public final class BeanNaturalKey {

  private final String[] naturalKey;
  private final BeanProperty[] props;
  private final Map<String, BeanProperty> propMap;
  private final Set<String> naturalKeyIdProperties;
  private final Map<String, String> idPropertyToParent;

  BeanNaturalKey(String[] naturalKey, BeanProperty[] props) {
    this.naturalKey = naturalKey;
    this.props = props;
    this.naturalKeyIdProperties = new HashSet<>();
    this.idPropertyToParent = new HashMap<>();

    Map<String, BeanProperty> propMap = new HashMap<>();

    for (int i = 0; i < props.length; i++) {
      BeanProperty prop = props[i];
      propMap.put(prop.name(), prop);
      if (prop instanceof BeanPropertyAssocOne) {
        BeanPropertyAssocOne<?> assocOne = (BeanPropertyAssocOne<?>) prop;
        STreeProperty idProperty = assocOne.targetDescriptor().idProperty();
        if (idProperty != null) {
          String idPath = naturalKey[i] + "." + idProperty.name();
          naturalKeyIdProperties.add(idPath);
          idPropertyToParent.put(idPath, naturalKey[i]);
        }
      }
    }
    this.propMap = propMap;
  }

  public int length() {
    return naturalKey.length;
  }

  /**
   * Return true if the property name is part of the natural key.
   * Also matches association ID properties (e.g., "asset.id" matches "asset").
   */
  public boolean matchProperty(String propName) {
    for (String key : naturalKey) {
      if (key.equals(propName)) {
        return true;
      }
    }
    return naturalKeyIdProperties.contains(propName);
  }

  /**
   * Return true if this is a single property natural key.
   */
  public boolean isSingleProperty() {
    return props.length == 1;
  }

  /**
   * Return true if the given propertyName is our natural key property.
   * Also matches association ID properties (e.g., "asset.id" matches "asset").
   */
  public boolean matchSingleProperty(String propertyName) {
    return naturalKey[0].equals(propertyName) || naturalKeyIdProperties.contains(propertyName);
  }

  /**
   * Return true if all the properties match our natural key.
   * Normalizes association ID properties (e.g., "asset.id" → "asset") before matching.
   */
  public boolean matchMultiProperties(Set<String> expressionProperties) {
    if (expressionProperties.size() != naturalKey.length) {
      return false;
    }

    Set<String> normalized = new HashSet<>(expressionProperties);

    for (String idPath : naturalKeyIdProperties) {
      if (normalized.remove(idPath)) {
        String parentProp = idPath.substring(0, idPath.lastIndexOf('.'));
        normalized.add(parentProp);
      }
    }

    if (normalized.size() != naturalKey.length) {
      return false;
    }

    for (String key : naturalKey) {
      if (!normalized.remove(key)) {
        return false;
      }
    }
    return normalized.isEmpty();
  }

  /**
   * Return the cache key given the bind values.
   * Normalizes bind values so that both "asset" and "asset.id" produce the same cache key.
   *
   * @param map The bind values for the properties.
   */
  public String calculateKey(Map<String, Object> map) {
    Map<String, Object> normalizedMap = new HashMap<>(map);

    for (Map.Entry<String, String> entry : idPropertyToParent.entrySet()) {
      String idPath = entry.getKey();
      String parentPath = entry.getValue();

      if (map.containsKey(idPath) && !map.containsKey(parentPath)) {
        Object idValue = map.get(idPath);
        BeanProperty prop = propMap.get(parentPath);
        if (prop == null) {
          throw new IllegalStateException("No property found for " + parentPath);
        }
        if (!(prop instanceof BeanPropertyAssocOne<?>)) {
          throw new IllegalStateException("Property " + parentPath + " is not an association");
        }
        BeanPropertyAssocOne<?> assocOne = (BeanPropertyAssocOne<?>) prop;
        Object beanRef = assocOne.targetDescriptor().createReference(idValue, null);
        normalizedMap.put(parentPath, beanRef);
      }
    }

    StringBuilder sb = new StringBuilder();
    for (BeanProperty prop : props) {
      sb.append(prop.naturalKeyVal(normalizedMap)).append(';');
    }
    return sb.toString();
  }
}
