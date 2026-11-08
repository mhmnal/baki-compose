import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Saves the whole X screen to a PNG. Used by shots.sh (Wayland-safe: capture works, input does not). */
public class DeskShot {
    public static void main(String[] args) throws Exception {
        BufferedImage img = new Robot().createScreenCapture(
            new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        ImageIO.write(img, "png", new File(args[0]));
    }
}
