import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class ImageProcessor {

  public enum Digit {
    COMMA(0x00000044, -1),
    ZERO(0x06864686, 0),
    ONE(0x0023aa11, 1),
    TWO(0x03544553, 2),
    THREE(0x002533a7, 3),
    FOUR(0x08816611, 4),
    FIVE(0x00563475, 5),
    SIX(0x06863652, 6),
    SEVEN(0x00355542, 7),
    EIGHT(0x03773773, 8),
    NINE(0x035322a9, 9);
    private long code;
    private int value;
    Digit(long code, int value) {
      this.code = code;
      this.value = value;
    }
  }
  private static Digit[] digits = Digit.values();
  private JFrame frame;
  private JPanel imagePanel;
  private BufferedImage debugImage;
  private Rectangle selected;
  private Rectangle defaultSelected;
  private Point mousePress;
  private Point mouseCurrent;
  private Robot robot;
  public enum Mode {
    NORMAL, WHITE_THRESHOLD, GOBLINS
  };
  private Mode mode = Mode.GOBLINS;
  public ImageProcessor(Rectangle selected) {
    try {
      robot = new Robot();
    } catch (AWTException e1) {
      e1.printStackTrace();
      System.exit(1);
    }
    this.defaultSelected = new Rectangle(selected);
    this.selected = new Rectangle(selected);
    frame = new JFrame("ImageProcessor");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    imagePanel = new JPanel() {
      @Override
      public void paintComponent(Graphics gg) {
        if( debugImage == null ) {
          return;
        }
        Graphics2D g = (Graphics2D)gg;
        if( mode == Mode.NORMAL ) {
          List<BufferedImage> goblinT = goblinColors(debugImage);
          g.drawImage(debugImage, 0, 0, getWidth(), getHeight(), null);
          g.setColor(Color.red);
          if( mousePress != null ) {
            if( mouseCurrent != null ) {
              g.draw(computeSelection());
            }
            else {
              g.drawRect(mousePress.x, mousePress.y, 1, 1);
            }
          }
        }
        else if( mode == Mode.GOBLINS ) {
          List<BufferedImage> goblinT = goblinColors(debugImage);
          g.drawImage(debugImage, 0, 0, getWidth()/2, getHeight()/2, null);
          g.drawImage(goblinT.get(0), getWidth()/2, 0, getWidth()/2, getHeight()/2, null);
          g.drawImage(goblinT.get(1), getWidth()/2, getHeight()/2, getWidth()/2, getHeight()/2, null);
        }
        else if( mode == Mode.WHITE_THRESHOLD ) {
          g.drawImage(debugImage, 0, 0, getWidth()/2, getHeight()/2, null);
          BufferedImage whiteT = whiteThreshold(debugImage, 255);
          g.drawImage(whiteT, getWidth()/2, 0, getWidth()/2, getHeight()/2, null);
          LinkedList<int[][]> digits = separateDigits(whiteT);
          long totalValue = 0;
          for( int[][] digit : digits) {
//            System.err.println("~~~~~~~~~~~");
//            ImageProcessor.print(digit);
            long code = reduceDigit(digit);
            int value = getValue(code);
            if( value == -1 ) {
//              System.err.print(",");
            } else {
              totalValue*=10;
              totalValue += value;
//              System.err.print(value);
            }
          }
//          System.err.println();
//          System.err.println(totalValue);
        }
      }
    };
    frame.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(KeyEvent arg0) {
        
      }
      @Override
      public void keyReleased(KeyEvent e) {
        if( e.getKeyCode() == KeyEvent.VK_SPACE ) {
          updateImage();
        }
        if( e.getKeyCode() == KeyEvent.VK_Q ) {
          mode = Mode.NORMAL;
        }
        if( e.getKeyCode() == KeyEvent.VK_W ) {
          mode = Mode.WHITE_THRESHOLD;
        }
        if( e.getKeyCode() == KeyEvent.VK_E ) {
          mode = Mode.GOBLINS;
        }
        frame.repaint();
      }
      @Override
      public void keyTyped(KeyEvent arg0) {
        
      }
    });
    imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        mouseCurrent = e.getPoint();
        frame.repaint();
      }
    });
    imagePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if( e.getButton() == MouseEvent.BUTTON1 ) {
          if( mode == Mode.NORMAL ) {
            mousePress = e.getPoint();
            frame.repaint();
          }
        }
        if( e.getButton() == MouseEvent.BUTTON2 ) {
          updateImage();
        }
        if( e.getButton() == MouseEvent.BUTTON3 ) {
          if( mode == Mode.NORMAL ) {
            ImageProcessor.this.selected = new Rectangle(defaultSelected);
            updateImage();
          }
        }
      }
      @Override
      public void mouseReleased(MouseEvent e) {
        if( e.getButton() == MouseEvent.BUTTON1 ) {
          if( mode == Mode.NORMAL ) {
            if( mouseCurrent != null ) {
              updateSelection(computeSelection());
              updateImage();
            }
            mousePress = null;
            mouseCurrent = null;
            frame.repaint();
          }
        }
      }
    });
    frame.setSize(640, 480);
    frame.setBounds(new Rectangle(960, 0, 480, 540));
    frame.add(imagePanel);
    frame.setVisible(true);
    updateImage();
  }
  public List<BufferedImage> goblinColors(BufferedImage image) {
    Graphics gg = image.getGraphics();
    gg.setColor(Color.black);
    gg.fillRect(718, 704 - GOBLIN_RECT.y, 300, 200);
    gg.fillRect(746, 32 - GOBLIN_RECT.y, 300, 190);
    gg.fillRect(567, 33 - GOBLIN_RECT.y, 500, 38);
    gg.dispose();
    boolean[][] goblins = new boolean[image.getWidth()][image.getHeight()];
    BufferedImage orig = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
    BufferedImage debug = new BufferedImage(FULL.width, FULL.height, image.getType());
    BufferedImage ret = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
    List<BufferedImage> images = new LinkedList<BufferedImage>();
    boolean[] table = new boolean[256*256*256];
    for( int y = 0; y < image.getHeight(); y++ ) {
      for( int x = 0; x < image.getWidth(); x++ ) {
        Color col = new Color(image.getRGB(x, y));
        int rgb = col.getRGB() & 0x00FFFFFF;
//        System.err.println(String.format("0x%h -> 0x%h", col.getRGB(), rgb));
        if(isGoblin(col)) {
          orig.setRGB(x, y, Color.black.getRGB());
          ret.setRGB(x, y, Color.white.getRGB());
          goblins[x][y] = true;
        }
        else {
          table[rgb] = true;
          orig.setRGB(x, y, col.getRGB());
          ret.setRGB(x, y, Color.BLACK.getRGB());
        }
      }
    }
    dilate(goblins);
    dilate(goblins);
    for( int y = 0; y < image.getHeight(); y++ ) {
      for( int x = 0; x < image.getWidth(); x++ ) {
        if( goblins[x][y] ) {
          ret.setRGB(x, y, Color.white.getRGB());
        }
      }
    }
    int index = 0;
    Graphics g = debug.getGraphics();
    int wid = 10;
    int x = 0;
    int y = 0;
    for (int counter = 0; counter < table.length; counter++) {
      if (table[counter]) {
        int rgb = counter | 0xFF000000;
        g.setColor(new Color(rgb));
        g.fillRect(x, y, wid, wid);
        x+=wid;
        if( x >= image.getWidth() ) {
          x = 0;
          y+=wid;
        }
        // ret.setRGB(index%image.getWidth(), index/image.getWidth(), new
        // Color(rgb).getRGB());
        // ret.setRGB((index+1)%image.getWidth(), index/image.getWidth(), new
        // Color(rgb).getRGB());
        // ret.setRGB((index+1)%image.getWidth(), index/image.getWidth()+1, new
        // Color(rgb).getRGB());
        // ret.setRGB(index%image.getWidth(), index/image.getWidth()+1, new
        // Color(rgb).getRGB());
        index += wid;
      }
    }
    g.dispose();
    images.add(ret);
    images.add(debug);
    return images;
  }
  public boolean[][] dilate(boolean[][] input) {
    boolean[][] output = new boolean[input.length][input[0].length];
    for( int x = 0; x < input.length; x++ ) {
      for( int y = 0; y < input[0].length; y++ ) {
        if( (x > 0 && input[x-1][y]) ||
            (x < input.length-1 && input[x+1][y]) ||
            (y > 0 && input[x][y-1]) ||
            (y < input[0].length - 1 && input[x][y+1]) ) {
          output[x][y] = true;
        }
      }
    }
    return output;
  }
  public boolean isGoblin(Color c) {
    double ratio1 = (double)(c.getGreen()) / c.getRed();
    double ratio2 = (double)(c.getGreen()) / c.getBlue();
    return (ratio1 > 1 && ratio1 < 1.06 && ratio2 > 2.6 && ratio2 < 2.9)
        || (ratio1 > 2.1 && ratio1 < 2.3 && ratio2 > 1.5 && ratio2 < 1.7);
  }
  public static void print(int[][] array) {
    for( int y = 0; y < array[0].length; y++ ) {
      String line = "";
      for( int x = 0; x < array.length; x++ ) {
        if( array[x][y] == 1 ) {
          line += "#";
        }
        else {
          line += " ";
        }
      }
      System.err.println(line);
    }
  }
  public static long getExperience(Robot robot) {
    BufferedImage expImage = robot.createScreenCapture(EXP_RECT);
    BufferedImage whiteT = whiteThreshold(expImage, 255);
    LinkedList<int[][]> digits = separateDigits(whiteT);
    long totalValue = 0;
    for( int[][] digit : digits) {
      long code = reduceDigit(digit);
      int value = getValue(code);
      if( value == -1 ) {
      } else if( value == -2 ) {
        print(digit);
//        System.err.println(code);
//        System.err.println(value);
        totalValue = -1;
        return totalValue;
      } else { 
        totalValue*=10;
        totalValue += value;
      }
    }
    return totalValue;
  }
  public static int getValue(long reducedDigit) {
    for( Digit d : digits) {
      if( d.code == reducedDigit ) {
        return d.value;
      }
    }
    return -2;
  }
  public static long reduceDigit(int[][] digit) {
    int[] vertSum = new int[digit.length];
    String vert = "";
    for( int x = 0; x < digit.length; x++ ) {
      for( int y = 0; y < digit[x].length; y++ ) {
        vertSum[x] += digit[x][y];
      }
      vert += vertSum[x] + ", ";
      
    }
//    System.err.println(vert);
    long ret = 0;
    for( int x = 0; x < vertSum.length; x++ ) {
      ret += vertSum[x];
      if( x != vertSum.length - 1) {
        ret = ret*16;
      }
    }
//    System.err.println(String.format("0x%08x", ret));
    return ret;
  }
  public static LinkedList<int[][]> separateDigits(BufferedImage image) {
    LinkedList<int[][]> ret = new LinkedList<int[][]>();
    boolean[] max = verticalMax(image);
    boolean active = false;
    int activeIndex = 0;
    for( int x = 0; x < max.length; x++ ) {
      if( !active ) {
        if( max[x] ) {
          active = true;
          activeIndex = x;
        }
      }
      else {
        if( !max[x] ) {
          int[][] digit = new int[x - activeIndex][image.getHeight()];
          for( int i = 0; i < digit.length; i++ ) {
            for( int j = 0; j < digit[i].length; j++ ) {
              digit[i][j] = (image.getRGB(activeIndex+i, j) == Color.white.getRGB())?1:0;
            }
          }
          ret.add(digit);
          active = false;
        }
      }
    }
    if( active ) {
      int[][] digit = new int[max.length - activeIndex][image.getHeight()];
      for( int i = 0; i < digit.length; i++ ) {
        for( int j = 0; j < digit[i].length; j++ ) {
          digit[i][j] = (image.getRGB(activeIndex+i, j) == Color.white.getRGB())?1:0;
        }
      }
      ret.add(digit);
      active = false;
    }
    return ret;
  }
  public static boolean[] verticalMax(BufferedImage image ) {
    boolean[] ret = new boolean[image.getWidth()];
    for( int x = 0; x < image.getWidth(); x++ ) {
      int maxRed = 0;
      int maxGreen = 0;
      int maxBlue = 0;
      for( int y = 0; y < image.getHeight(); y++ ) {
        Color col = new Color(image.getRGB(x, y));
        maxRed = Math.max(col.getRed(), maxRed);
        maxGreen = Math.max(col.getGreen(), maxGreen);
        maxBlue = Math.max(col.getBlue(), maxBlue);
      }
      Color col = new Color(maxRed, maxGreen, maxBlue);
      ret[x] = col.equals(Color.WHITE);
    }
    return ret;
  }
  public static BufferedImage whiteThreshold(BufferedImage image, int value) {
    BufferedImage ret = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
    for( int y = 0; y < image.getHeight(); y++ ) {
      for( int x = 0; x < image.getWidth(); x++ ) {
        Color col = new Color(image.getRGB(x, y));
        if( col.getRed() >= value && col.getGreen() >= value && col.getBlue() >= value) {
          ret.setRGB(x, y, Color.white.getRGB());
        }
        else {
          ret.setRGB(x, y, Color.BLACK.getRGB());
        }
      }
    }
    return ret;
  }
  public void updateSelection(Rectangle newSelection) {
    selected = new Rectangle(
        selected.x + selected.width*newSelection.x/imagePanel.getWidth(),
        selected.y + selected.height*newSelection.y/imagePanel.getHeight(),
        selected.width*newSelection.width/imagePanel.getWidth(),
        selected.height*newSelection.height/imagePanel.getHeight()
        );
    System.err.println("new selection = " + selected.x + ", " + selected.y + ", " + selected.width + ", " + selected.height);
  }
  public Rectangle computeSelection() {
    return new Rectangle( Math.min(mousePress.x, mouseCurrent.x), Math.min(mousePress.y, mouseCurrent.y), Math.abs(mousePress.x - mouseCurrent.x), Math.abs(mousePress.y - mouseCurrent.y));
  }
  public void updateImage() {
    debugImage = robot.createScreenCapture(selected);
    frame.repaint();
  }
  public static final Rectangle EXP_RECT = new Rectangle(581, 41, 122, 10);
  public static final Rectangle FULL = new Rectangle(0, 0, 960, 1080);
  public static final Rectangle GOBLIN_RECT = new Rectangle(0, 31, 960, 843);
  public static final Rectangle GOBLIN_INV_RECT = new Rectangle(718, 704, 250, 180);
  public static void main(String[] args) {
    ImageProcessor imgP = new ImageProcessor(EXP_RECT);
  }
}
