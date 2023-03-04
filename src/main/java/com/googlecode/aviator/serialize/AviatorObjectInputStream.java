package com.googlecode.aviator.serialize;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.BaseExpression;
import com.googlecode.aviator.ClassExpression;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.code.asm.ClassDefiner;
import com.googlecode.aviator.parser.AviatorClassLoader;
import com.googlecode.aviator.utils.Reflector;

/**
 * A special ObjectInputStream that loads a class based on the AvaitorClassLoader rather than the
 * system default.
 * 
 * @since 5.3.4
 * @author dennis
 *
 */
public class AviatorObjectInputStream extends ObjectInputStream {
  private AviatorClassLoader classLoader;
  private AviatorEvaluatorInstance instance;
  private Map<String, byte[]> classBytesCache = new HashMap<String, byte[]>();

  public AviatorObjectInputStream(InputStream in, AviatorEvaluatorInstance instance)
      throws IOException {
    super(in);
    this.classLoader = instance.getAviatorClassLoader(true);
    this.instance = instance;
    this.enableResolveObject(true);
  }

  @Override
  protected Object resolveObject(Object obj) throws IOException {
    Object object = super.resolveObject(obj);
    if (object instanceof BaseExpression) {
      BaseExpression exp = (BaseExpression) object;
      exp.setInstance(this.instance);
      if (exp.getCompileEnv() != null) {
        exp.getCompileEnv().setInstance(this.instance);
      }
      if (object instanceof ClassExpression) {
        ((ClassExpression) object)
            .setClassBytes(this.classBytesCache.get(object.getClass().getName()));
      }
    }
    return object;
  }

  @Override
  protected Class<?> resolveClass(ObjectStreamClass desc)
      throws IOException, ClassNotFoundException {
    Class<?> clazz = null;
    try {
      clazz = super.resolveClass(desc);
    } catch (ClassNotFoundException e) {

    }
    if (clazz == null && desc.getName().startsWith("AviatorScript_")) {
      int len = this.readInt();
      byte[] classBytes = new byte[len];

      int readed = 0;
      while (readed < classBytes.length) {
        int n = this.read(classBytes, readed, classBytes.length - readed);
        if (n < 0) {
          break;
        }
        readed += n;
      }

      String name = desc.getName();
      this.classBytesCache.put(name, classBytes);
      try {
        // already in class loader
        clazz = Class.forName(name, false, this.classLoader);
      } catch (ClassNotFoundException ex) {

      }
      if (clazz == null) {
        // still not found, try to define it.
        try {
          clazz = ClassDefiner.defineClass(desc.getName(), Expression.class, classBytes,
              this.classLoader, true);
        } catch (Throwable t) {
          throw Reflector.sneakyThrow(t);
        }
      }

    }
    if (clazz == null) {
      throw new ClassNotFoundException("Class not found:" + desc.getName());
    }
    return clazz;
  }

}
