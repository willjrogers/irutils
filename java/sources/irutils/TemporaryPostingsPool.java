package irutils;
import java.io.*;
import java.util.*;

/**
 * TemporaryPostingsPool.java
 *
 *
 * Created: Wed Sep 19 16:41:56 2001
 *
 * @author <a href="mailto: "Willie Rogers</a>
 * @version 0.01
 */

public class TemporaryPostingsPool implements Serializable {
  String postingsFilename = "tpost";
  transient private RandomAccessFile postingsRAF = null;
  static final int BUFFER_SIZE = 1500;
  byte[] buffer = new byte[BUFFER_SIZE];
  int lastIndex = 0;

  public TemporaryPostingsPool ()
  {
    try {
      this.postingsRAF =  new RandomAccessFile(this.postingsFilename, "rw");
    } catch (FileNotFoundException exception) {
      exception.printStackTrace(System.err);
    }
  }

  public TemporaryPostingsPool (String aPostingsFilename)
  {
    this.postingsFilename = aPostingsFilename;
    try {
      this.postingsRAF =  new RandomAccessFile(this.postingsFilename, "rw");
    } catch (FileNotFoundException exception) {
      exception.printStackTrace(System.err);
    }
  }

  public TemporaryPostingsPool (String aPostingsFilename, String mode)
  {
    this.postingsFilename = aPostingsFilename;
    try {
      this.postingsRAF =  new RandomAccessFile(this.postingsFilename, mode);
    } catch (FileNotFoundException exception) {
      exception.printStackTrace(System.err);
    }
  }

  public void openPostings()
  {
    try {
      this.postingsRAF =  new RandomAccessFile(this.postingsFilename, "rw");
    } catch (FileNotFoundException exception) {
      exception.printStackTrace(System.err);
    }
  }

  public void openPostings(String mode)
  {
    try {
      this.postingsRAF =  new RandomAccessFile(this.postingsFilename, mode);
    } catch (FileNotFoundException exception) {
      exception.printStackTrace(System.err);
    }
  }

  /**
   * @param posting string data to post 
   * @param link address of previous posting in list.
   * @return address of posting
   */
  public int add(String posting, int link)
  {
    int address = -1;
    try {
      byte[] bytes = posting.getBytes();
      this.postingsRAF.writeInt(bytes.length);
      this.postingsRAF.write(bytes);
      this.postingsRAF.writeInt(link);
      address = this.lastIndex;
      this.lastIndex = this.lastIndex + bytes.length + 8;
    } catch (Exception exception) {
      System.err.println("add(): exception: " + exception.getMessage());
    }
    return address;
  }

  public List<String> get(int address)
  {
    List<String> aList = new ArrayList<String>();
    int length = 0;
    int link = address;
    try {
      while (link != -1) {
	this.postingsRAF.seek(link);
	length = this.postingsRAF.readInt();
	this.postingsRAF.read(buffer, 0, length);
	aList.add(new String(buffer, 0, length).intern());
	link = this.postingsRAF.readInt();
      }
    } catch (Exception exception) {
      System.err.println("get(): exception: " + exception.getMessage());
    }
    return aList;
  }

  public List<String> getv2(int address)
  {
    return new TemporaryPostingsList(this.postingsRAF, address);
  }

  public String getPosting(int address)
  {
    try {
      this.postingsRAF.seek(address);
      int length = this.postingsRAF.readInt();
      this.postingsRAF.read(buffer, 0, length);
      return new String(buffer, 0, length);
    } catch (Exception exception) {
      System.err.println("get(): exception: " + exception.getMessage());
    }
    return null;
  }

  public void close()
  { 
    try {
      this.postingsRAF.close();
    } catch (Exception exception) {
      System.err.println("get(): exception: " + exception.getMessage());
    }
  }


  private class TemporaryPostingsList extends AbstractList<String>
    implements List<String>
  {
    int address;
    RandomAccessFile postingsRAF;
    byte[] buffer = new byte[TemporaryPostingsPool.BUFFER_SIZE];

    TemporaryPostingsList(RandomAccessFile raf, int newAddress)
    {
      this.postingsRAF = raf;
      this.address = newAddress;
    }
    public String get(int index)
    {
      int i = 0;
      int length = 0;
      int link = this.address;
      byte[] buffer = null;
      try {
	while (link != -1) {
	  this.postingsRAF.seek(link);
	  length = this.postingsRAF.readInt();
	  this.postingsRAF.read(this.buffer, 0, length);
	  link = this.postingsRAF.readInt();
	  if (index == i) {
	    return new String(this.buffer, 0, length);
	  }
	  i++;
	}
      } catch (Exception exception) {
	System.err.println("get(): exception: " + exception.getMessage());
      }
      return null;
    }
    public int size()
    {
      int i = 0;
      int length = 0;
      int link = this.address;
      try {
	while (link != -1) {
	  this.postingsRAF.seek(link);
	  length = this.postingsRAF.readInt();
	  this.postingsRAF.read(this.buffer, 0, length);
	  link = this.postingsRAF.readInt();
	  i++;
	}
      } catch (Exception exception) {
	System.err.println("get(): exception: " + exception.getMessage());
      }
      return i;
    }
  }

  private class PostingsListIterator implements Iterator<String>, ListIterator<String>
  {
    int index = 0;
    int link;
    int length = 0;
    byte[] buffer = new byte[TemporaryPostingsPool.BUFFER_SIZE];
    RandomAccessFile postingsRAF;

    PostingsListIterator (RandomAccessFile raf, int newAddress)
    {
      this.postingsRAF = raf;
      this.link = newAddress;
    }

    public void 	add(String o) { }
    public boolean 	hasNext() {
      return this.link != -1;
    }
    public boolean 	hasPrevious() { return false; }
    public String 	next()
    {
      try {
	this.postingsRAF.seek(link);
	length = this.postingsRAF.readInt();
	this.postingsRAF.read(buffer, 0, length);
	link = this.postingsRAF.readInt();
	index++;
      } catch (Exception exception) {
	System.err.println("get(): exception: " + exception.getMessage());
      }
      return new String(buffer, 0, length);
    }
    public int 	nextIndex() {
      return index;
    }
    public String previous() {
      return null;
    }
    public int previousIndex() {
      return index - 1;
    }
    public void remove() { }
    public void set(String o) {}

  }

}// TemporaryPostingsPool
