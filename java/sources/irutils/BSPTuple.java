package utils;

/**
 * BSPTuple.java
 *
 *
 * Created: Wed Jul 18 13:10:42 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPTuple.java,v 1.4 2001/08/27 18:50:19 wrogers Exp $
 */

public class BSPTuple {
  private String key;
  private Object value;
  /**
   * instantiate new key/value pair object (tuple)
   * @param key   key for tuple.
   * @param value value for key
   */
  public BSPTuple (String key, Object value)
  {
    this.key = key;
    this.value = value;
  }

  /**
   * Implementation of toString to override default implementation in
   * java.lang.Object.
   * @return string representation of tuple object.
   */  
  public String toString()
  {
    return "key: " + this.key + ", value: " + value;
  }
}// BSPTuple
