package utils;
import java.util.*;

/**
 * StringUtils.java
 *
 *
 * Created: Tue Jul 10 09:05:44 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: StringUtils.java,v 1.2 2001/07/25 18:44:23 wrogers Exp $
 */

public final class StringUtils extends Object
{

  /**
   * Split string into substrings based on delimitor characters supplied;
   * resulting substrings are placed in array list in order of appearance.
   *
   * @param textstring string to be split.
   * @param chars delimitor characters.
   * @return ArrayList containing substrings or empty list if no strings
   *         were split.
   */
  public static ArrayList split(String textstring, String chars)
  {
    StringTokenizer st = new StringTokenizer(textstring, chars);
    ArrayList list = new ArrayList(st.countTokens());
    while (st.hasMoreTokens()) 
      {
	list.add(st.nextToken());
      }
    return list;
  }

  /**
   * Split string into substrings based on delimitor characters supplied;
   * return substring specified by position.  If there are less tokens than
   * pos then return null.
   *
   * @param textstring string to be split.
   * @param chars delimitor characters.  
   * @param pos position of substring to be returned.
   * @return substring at position pos or null if string has less tokens than pos.
   */

  public static String getToken(String textstring, String chars, int pos)
  {
    int i = 0;
    StringTokenizer st = new StringTokenizer(textstring, chars);
    while (st.hasMoreTokens()) {
      String tok = (String)st.nextToken();
      if (i == pos) return tok;
      i++;
    }
    return null;
  }

  /**
   * Write out string elements of arraylist as a brace encapsulated
   * list separated by spaces; if a token contains spaces then
   * encapsulate the token with braces.
   *
   * @param list  array list of strings to be joined
   * @return string joined strings of array list separated by spaces
   *         encapsulated by braces.
   */
  public static String list(ArrayList list)
  {
    StringBuffer sb = new StringBuffer();
    Iterator it = list.iterator();
    sb.append("{");
    while ( it.hasNext() )
      {
	String token = (String)it.next();
	if ( token.indexOf(" ") == -1 ) {
	  sb.append(token);
	} else {
	  // if token contains spaces then encapsulate token with braces.
	  sb.append("{");
	  sb.append(token);
	  sb.append("}");
	}
	if (it.hasNext()) sb.append(" ");
      }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Write out string elements of arraylist separated by joinstring specified
   * by user.
   * @param list       array list of strings to be joined
   * @param joinString character to join strings together
   * @return string containing joined strings of array list separated by
   *         joinstring.
   */
  public static String join(ArrayList list, String joinString)
  {
    StringBuffer sb = new StringBuffer();
    Iterator it = list.iterator();
    while ( it.hasNext() )
      {
	sb.append(it.next());
	if (it.hasNext()) sb.append(joinString);
      }
    return sb.toString();
  }

} // StringUtils

