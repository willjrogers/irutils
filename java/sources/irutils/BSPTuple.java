package utils;

/**
 * BSPTuple.java
 *
 *
 * Created: Wed Jul 18 13:10:42 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version
 */

public class BSPTuple {
  private String key;
  private String value;
  public BSPTuple (String key, String value)
  {
    this.key = key;
    this.value = value;
  }

  public String toString()
  {
    return "key: " + this.key + ", value: " + value;
  }
}// BSPTuple
