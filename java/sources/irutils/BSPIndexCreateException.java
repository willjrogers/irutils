package utils;

/**
 * BSPIndexCreateException.java
 *
 *
 * Created: Fri Jul 20 10:51:39 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version 0.01
 */

public class BSPIndexCreateException extends Exception {
  public BSPIndexCreateException () {
    super("BSPIndexCreateException");
  }
  public BSPIndexCreateException (String message) {
    super(message);
  }
}// BSPIndexCreateException
