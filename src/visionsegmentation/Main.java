package visionsegmentation;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Vision HW6
 * Use the Laws texture energy measures and the K-mean clustering algorithm
 * to perform segmentation on the zebra image. Set the K to 4, 5, and 6 in
 * the clustering algorithm and compare the results. Show the segmentation
 * output by assigning different grey tones to the pixels in different
 * clusters.
 * 
 * @author Bryan P.
 */
public class Main {

    private static final int SIZE = 256;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        int[][] image = read("zebras.raw");
        double[][][] maskedImage = applyLaws(toDouble(image));
        int[][] result4 = applySegmentation(maskedImage, 4);
        int[][] result5 = applySegmentation(maskedImage, 5);
        int[][] result6 = applySegmentation(maskedImage, 6);
        show(image, result4, result5, result6);
    }

    private static int[][] applySegmentation(double[][][] maskedImage, int numMeans) {
        double[][] means = new double[numMeans][9];
        Map<Integer, List<int[]>> segmentMap = new HashMap<Integer, List<int[]>>();
        Random r = new Random();

        //initialize with randomly selected means
        for (int i = 0; i < numMeans; i++) {
            means[i] = maskedImage[r.nextInt(SIZE)][r.nextInt(SIZE)];
        }

        boolean done;
        do {
            //initialize segment map
            for (int i = 0; i < numMeans; i++) {
                segmentMap.put(i, new ArrayList<int[]>());
            }

            //for each pixel
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {

                    double closest_distance = Double.MAX_VALUE;
                    int closest_mean = -1;

                    for (int k = 0; k < numMeans; k++) {
                        double distance = 0;
                        //calculate distance
                        for (int l = 0; l < 9; l++) {
                            distance += Math.pow(means[k][l] - maskedImage[i][j][l], 2);
                        }
                        distance = Math.sqrt(distance);

                        //find closest mean
                        if (distance < closest_distance) {
                            closest_distance = distance;
                            closest_mean = k;
                        }
                    }
                    
                    //assign this pixel to closest mean
                    int[] pixel = {i, j};
                    segmentMap.get(closest_mean).add(pixel);
                }
            }

            //update means
            done = true;
            for (int i = 0; i < numMeans; i++) {
                double[] newMean = {0, 0, 0, 0, 0, 0, 0, 0, 0};
                List<int[]> pixels = segmentMap.get(i);
                //calculate mean for all pixels in this segment
                for (int[] pixel : pixels) {
                    int x = pixel[0];
                    int y = pixel[1];
                    for (int j = 0; j < 9; j++) {
                        newMean[j] += maskedImage[x][y][j];
                    }
                    for (int j = 0; j < 9; j++) {
                        newMean[j] = newMean[j] / 9;
                    }
                }

                //check if new mean is different from current mean
                double delta_mean = 0;
                for (int j = 0; j < 9; j++) {
                    delta_mean = Math.pow(means[i][j] - newMean[j], 2);
                }
                
                delta_mean = Math.sqrt(delta_mean);
                if (delta_mean != 0) {
                    done = false;
                }
                System.out.println("mean: " + i + ", delta: " + delta_mean);
                means[i] = newMean;
            }
            System.out.println();

        } while (!done); //continue until no vector changes cluster

        /* covert segments into an image where
         * segment is a different shade of grey,
         * evenly distributed relative to the
         * number of means
         */
        int[][] result = new int[SIZE][SIZE];
        double scale = Integer.MAX_VALUE / numMeans;
        for (int i = 0; i < numMeans; i++) {
            List<int[]> pixels = segmentMap.get(i);
            for (int[] pixel : pixels) {
                int x = pixel[0];
                int y = pixel[1];
                result[x][y] = (int) (i * scale);
            }
        }
        return result;
    }

    private static double[][][] applyLaws(double[][] image) {
        double[][] l5 = {{1, 4, 6, 4, 1}};
        double[][] e5 = {{-1, -2, 0, 2, 1}};
        double[][] s5 = {{-1, 0, 2, 0, -1}};
        double[][] r5 = {{1, -4, 6, -4, 1}};

        /* Pre-processing:
         * Remove illumination by subtracting the
         * local average from each pixel in a 15x15
         * neighborhood
         */
        int neighborhood = 15;
        double[][] processedImage = new double[SIZE][SIZE];
        //for each pixel
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {

                //subtract average of 15x15 neighborhood from pixel
                double average = 0;
                for (int k = -neighborhood / 2; k < neighborhood / 2; k++) {
                    for (int l = -neighborhood / 2; l < neighborhood / 2; l++) {
                        //@todo: dont use exceptions for flow control
                        try {
                            average += image[i + k][j + l];
                        } catch (ArrayIndexOutOfBoundsException e) {
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

        double[][] map1 = applyMask(processedImage, average(multiply(l5, transpose(e5)), multiply(e5, transpose(l5))));
        double[][] map2 = applyMask(processedImage, average(multiply(l5, transpose(s5)), multiply(s5, transpose(l5))));
        double[][] map3 = applyMask(processedImage, average(multiply(l5, transpose(r5)), multiply(r5, transpose(l5))));
        double[][] map4 = applyMask(processedImage, multiply(e5, transpose(e5)));
        double[][] map5 = applyMask(processedImage, average(multiply(e5, transpose(s5)), multiply(s5, transpose(e5))));
        double[][] map6 = applyMask(processedImage, average(multiply(e5, transpose(r5)), multiply(r5, transpose(e5))));
        double[][] map7 = applyMask(processedImage, multiply(s5, transpose(s5)));
        double[][] map8 = applyMask(processedImage, average(multiply(s5, transpose(r5)), multiply(r5, transpose(s5))));
        double[][] map9 = applyMask(processedImage, multiply(r5, transpose(r5)));

        /*
         * Combine all the maps into one image with
         * 9 dimensional vectors as pixels
         */
        double[][][] result = new double[SIZE][SIZE][9];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                for (int k = 0; k < 9; k++) {
                    double value = 0;
                    switch (k) {
                        case 0:
                            value = map1[i][j];
                            break;
                        case 1:
                            value = map2[i][j];
                            break;
                        case 2:
                            value = map3[i][j];
                            break;
                        case 3:
                            value = map4[i][j];
                            break;
                        case 4:
                            value = map5[i][j];
                            break;
                        case 5:
                            value = map6[i][j];
                            break;
                        case 6:
                            value = map7[i][j];
                            break;
                        case 7:
                            value = map8[i][j];
                            break;
                        case 8:
                            value = map9[i][j];
                            break;
                        default:
                            break;
                    }
                    result[i][j][k] = value;
                }
            }
        }
        return result;
    }

    /**
     * @param image a square SIZExSIZE matrix
     * @param mask a square MxM matrix
     * @return a square SIZExSIZE matrix which has had the specified mask applied to each pixel
     */
    private static double[][] applyMask(double[][] image, double[][] mask) {
        double[][] result = new double[SIZE][SIZE];

        //for each pixel in image
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                result[i][j] = 0;

                int maskSize = mask.length;
                for (int k = 0; k < maskSize; k++) {
                    for (int l = 0; l < maskSize; l++) {
                        try {
                            result[i][j] += image[i + k - maskSize / 2][j + l - maskSize / 2] * mask[k][l];
                        } catch (ArrayIndexOutOfBoundsException e) {
                        }
                    }
                }

            }
        }
        return result;
    }

    /**
     * convert int[][] to double[][]
     */
    private static double[][] toDouble(int[][] image) {
        double[][] fimage = new double[SIZE][SIZE];

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                fimage[i][j] = (double) image[i][j];
            }
        }
        return fimage;
    }

    /**
     * @param a square MxM matrix
     * @param b square MxM matrix
     * @return square MxM matrix
     */
    private static double[][] average(double[][] a, double[][] b) {
        int size = a.length;
        double[][] c = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
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
    private static double[][] multiply(double[][] a, double[][] b) {
        int l = a[0].length;
        int m = a.length;
        int n = b.length;
        double[][] c = new double[l][n];

        for (int i = 0; i < l; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < m; k++) {
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
    private static double[][] transpose(double[][] a) {
        int m = a.length;
        int n = a[0].length;
        double[][] b = new double[n][m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                b[j][i] = a[i][j];
            }
        }

        return b;
    }

    /**
     * Show images in message box
     */
    private static void show(int[][]... images) {
        JPanel panel = new JPanel();
        for (int[][] image : images) {
            BufferedImage bimage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int value = image[y][x] << 16 | image[y][x] << 8 | image[y][x];
                    bimage.setRGB(x, y, value);
                }
            }
            panel.add(new JLabel(new ImageIcon(bimage)));
        }

        JOptionPane.showMessageDialog(null, panel);
    }

    /**
     * Read image from file
     */
    public static int[][] read(String file) {
        int[][] image = new int[SIZE][SIZE];
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    image[i][j] = inputStream.read();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return image;
    }
}
