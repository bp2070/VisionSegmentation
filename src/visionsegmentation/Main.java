package visionsegmentation;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 *
 * @author bryan
 */
public class Main {
    
    private static final int SIZE = 256;
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int[][] image = read("zebras.raw");
        List<double[][]> maskedImage = applyLaws(toDouble(image));
    }

    private static List<double[][]> applyLaws(double[][] image){
        double[][] l5 = {{1,4,6,4,1}};
        double[][] e5 = {{-1, -2, 0, 2, 1}};
        double[][] s5 = {{-1,0,2,0,-1}};
        double[][] r5 = {{1,-4,6,-4,1}};

        /* Pre-processing:
         * Remove illumination by subtracting the
         * local average from each pixel in a 15x15
         * neighborhood
         */
        int neighborhood = 15;
        double[][] processedImage = new double[SIZE][SIZE];
        //for each pixel
        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){

                //subtract average of 15x15 neighborhood from pixel
                double average = 0;
                for(int k = -neighborhood/2; k < neighborhood/2; k++){
                    for(int l = -neighborhood/2; l < neighborhood/2; l++){
                        //@todo: dont use exceptions for flow control
                        try{
                            average += image[i+k][j+l];
                        }catch(ArrayIndexOutOfBoundsException e){                            
                        }
                    }
                }
                average = average / (neighborhood * neighborhood);
                processedImage[i][j] = image[i][j] - average;
            }
        }

        /* Filtering:
         * Each of 16 5x5 masks are applied to the
         * image post-processing, resulting in 16
         * filtered images.
         */

        /*
         * Previously created 16 maps are combined by
         * specific symmetric pairs to produce nine
         * final maps
         */

        double[][] map1 = average(multiply(l5, transpose(e5)), multiply(e5, transpose(l5)));        
        double[][] map2 = average(multiply(l5, transpose(s5)), multiply(s5, transpose(l5)));
        double[][] map3 = average(multiply(l5, transpose(r5)), multiply(r5, transpose(l5)));
        double[][] map4 = multiply(e5, transpose(e5));
        double[][] map5 = average(multiply(e5, transpose(s5)), multiply(s5, transpose(e5)));
        double[][] map6 = average(multiply(e5, transpose(r5)), multiply(r5, transpose(e5)));
        double[][] map7 = multiply(s5, transpose(s5));
        double[][] map8 = average(multiply(s5, transpose(r5)), multiply(r5, transpose(s5)));
        double[][] map9 = multiply(r5, transpose(r5));

        List<double[][]> result = new ArrayList<double[][]>(9);
        result.add(applyMask(processedImage, map1));
        result.add(applyMask(processedImage, map2));
        result.add(applyMask(processedImage, map3));
        result.add(applyMask(processedImage, map4));
        result.add(applyMask(processedImage, map5));
        result.add(applyMask(processedImage, map6));
        result.add(applyMask(processedImage, map7));
        result.add(applyMask(processedImage, map8));
        result.add(applyMask(processedImage, map9));

        return result;
    }

    /**
     * @param image a square SIZExSIZE matrix
     * @param mask a square MxM matrix
     * @return a square SIZExSIZE matrix which has had the specified mask applied to each pixel
     */
    private static double[][] applyMask(double[][] image, double[][] mask)
    {
        double[][] result = new double[SIZE][SIZE];

        //for each pixel in image
        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                result[i][j]=0;

                int maskSize = mask.length;
                for(int k = 0; k < maskSize; k++){
                    for(int l = 0; l < maskSize; l++){
                        try{
                            result[i][j] += image[i+k-maskSize/2][j+l-maskSize/2] * mask[k][l];
                        } catch (ArrayIndexOutOfBoundsException e) {}
                    }
                }
                
            }
        }
        return result;
    }

    private static double[][] toDouble(int[][] image){
        double[][] fimage = new double[SIZE][SIZE];

        for(int i = 0; i < SIZE; i++){
            for(int j = 0; j < SIZE; j++){
                fimage[i][j] = (double)image[i][j];
            }
        }
        return fimage;
    }

    /**
     * @param a square MxM matrix
     * @param b square MxM matrix
     * @return square MxM matrix
     */
    private static double[][] average(double[][]a, double[][]b){
        int size = a.length;
        double[][] c = new double[size][size];

        for(int i = 0; i < size; i++){
            for(int j = 0; j < size; j++){
                c[i][j] = (a[i][j] + b[i][j]) / 2;
            }
        }

        return c;
    }

    /**
     * @param a is LxM matrix
     * @param b is MxN matrix
     * @return LxN matrix
     */
    private static double[][] multiply(double[][] a, double[][] b){
        int l = a[0].length;
        int m = a.length;
        int n = b.length;
        double[][] c = new double[l][n];
        
        for(int i = 0; i < l; i++){
            for(int j = 0; j < n; j++){
                for(int k = 0; k < m; k++){
                    c[i][j] += a[k][i] * b[j][k];
                }
            }
        }
        return c;
    }

    /**
     * @param a MxN matrix
     * @return a NxM matrix
     */
    private static double[][] transpose(double[][] a){
        int m = a.length;
        int n = a[0].length;
        double[][] b = new double[n][m];

        for(int i = 0; i < m; i++){
            for(int j = 0; j < n; j++){
                b[j][i] = a[i][j];
            }
        }

        return b;
    }

    private static void print(double[][] a){
        for(int i = 0; i < a.length; i++){
            System.out.print("{");
            for(int j = 0; j < a[i].length; j++){
                System.out.print(a[i][j]);
                if(j < a[i].length-1){
                    System.out.print(", ");
                }
            }
                System.out.print("}\n");
        }
        System.out.print("\n");
    }

    private static void show(double[][] image){
        BufferedImage bimage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for(int y = 0; y < SIZE; y++){
            for(int x = 0; x < SIZE; x++){
                int value = (int)image[y][x] << 16 | (int)image[y][x] << 8 | (int)image[y][x];
                bimage.setRGB(x, y, value);
            }
        }

        JOptionPane.showMessageDialog(null, new ImageIcon(bimage));
    }

    public static int[][] read(String file){
        int[][] image = new int[SIZE][SIZE];
        InputStream inputStream = null;
        try{
            inputStream = new FileInputStream(file);
        } catch(FileNotFoundException e){
            e.printStackTrace();
        }

        try{
            for(int i = 0; i < SIZE; i++) {
                for(int j = 0; j < SIZE; j++) {
                    image[i][j] = inputStream.read();
                }
            }
        } catch(IOException e){
            e.printStackTrace();
        } finally {
            try{
                inputStream.close();
            } catch(IOException e){
                e.printStackTrace();
            }
        }
        return image;
    }
}
