package utils;

/**
 * BSPIndexCreateException.java
 *
 *
 * Created: Fri Jul 20 10:51:39 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPIndexCreateException.java,v 1.3 2001/07/26 19:03:15 wrogers Exp $
 */

public class BSPIndexCreateException extends Exception {
  /**
   * Instantiate new index creation exception.
   */
  public BSPIndexCreateException () {
    super("BSPIndexCreateException");
  }
  /**
   * Instantiate new index creation exception.
   * @param message additional information about exception.
   */
  public BSPIndexCreateException (String message) {
    super(message);
  }
}// BSPIndexCreateException
