package com.example.SudokuSolver;

import static com.example.SudokuSolver.R.*;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.app.AlertDialog;
import android.Manifest;


import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import logic.SudokuGrid;
import logic.Tuple;


import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;


public class MainActivity extends AppCompatActivity {

    //camera permission constant
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    //the main sudoku grid cells (they're buttons so they can be selected and highlighted)
    final Button[][] cells = new Button[9][9];

    //these ints determine the selected cell (i.e., which cell the numpad buttons write into)
    private int selectedRow = -1;
    private int selectedCol = -1;
    private Button cameraButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GridLayout sudokuGrid = findViewById(R.id.sudoku_grid);
        GridLayout numpadGrid = findViewById(R.id.numpad_grid);
        GridLayout operationsGrid = findViewById(id.grid_operations);

        TextView outputText = findViewById(id.output_text);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        previewView = findViewById(id.previewView);

        //dynamically create 9x9 Sudoku grid using button style from XML
        for (int row = 0; row < 9; row++)
            for (int col = 0; col < 9; col++) {
                Button button = new Button(new ContextThemeWrapper(this, R.style.SudokuButtonStyle2));
                if(needsSecondColor(row, col)) //set the background depending on if it's in the top, bottom, left or right box
                    button = new Button(new ContextThemeWrapper(this, R.style.SudokuButtonStyle1));

                button.setText("");
                button.setTag(new int[]{row, col}); //tag stores row and column

                button.setOnClickListener(v -> {
                    //unhighlight previous cell
                    if(selectedRow >= 0 && selectedCol >= 0)
                        if (needsSecondColor(selectedRow, selectedCol))
                            cells[selectedRow][selectedCol].setBackgroundResource(drawable.button_background2);
                        else
                            cells[selectedRow][selectedCol].setBackgroundResource(drawable.button_background1);
                    int[] tag = (int[]) v.getTag();
                    selectedRow = tag[0];
                    selectedCol = tag[1];
                    //highlight selected cell
                    cells[selectedRow][selectedCol].setBackgroundColor(getResources().getColor(color.colorAccent));
                });

                cells[row][col] = button;
                if(needsSecondColor(row, col))
                    cells[row][col].setBackgroundResource(R.drawable.button_background2);
                else
                    cells[row][col].setBackgroundResource(R.drawable.button_background1);

                sudokuGrid.addView(button);
            }

        //listener to adjust button size when the grid is laid out
        sudokuGrid.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            //get the available width and height for the grid
            int gridWidth = sudokuGrid.getWidth();
            int gridHeight = sudokuGrid.getHeight();

            //get size for the square buttons based on the smaller dimension
            int buttonSize = Math.min(gridWidth / 9, gridHeight / 9);

            //set each button's size to be square
            for (int row = 0; row < 9; row++)
                for (int col = 0; col < 9; col++) {
                    Button button = cells[row][col];
                    GridLayout.LayoutParams params = (GridLayout.LayoutParams) button.getLayoutParams();
                    params.width = buttonSize;
                    params.height = buttonSize;
                    button.setLayoutParams(params);
                }
        });

        //dynamically create numpad buttons (1-9) using button style from XML
        for (int num = 1; num <= 9; num++) {
            Button numButton = new Button(new ContextThemeWrapper(this, R.style.NumpadButtonStyle));
            numButton.setText(String.valueOf(num));
            numButton.setOnClickListener(v -> {
                if (selectedRow >= 0 && selectedCol >= 0) {
                    int index = selectedRow * 9 + selectedCol;
                    Button sudokuButton = (Button) sudokuGrid.getChildAt(index);
                    sudokuButton.setText(((Button) v).getText().toString());
                    outputText.setText("");
                }
            });
            numpadGrid.addView(numButton);
        }

        //this button is used to clear the cell, i.e. set the cell to 0 / empty
        Button clearButton = new Button(new ContextThemeWrapper(this, R.style.NumpadButtonStyle));
        clearButton.setText("");
        clearButton.setOnClickListener(v -> {
            if (selectedRow >= 0 && selectedCol >= 0) {
                int index = selectedRow * 9 + selectedCol;
                Button sudokuButton = (Button) sudokuGrid.getChildAt(index);
                sudokuButton.setText("");
            }
        });
        numpadGrid.addView(clearButton);

        //this button provides a hint (i.e. the correct digit for the selected cell) and inserts it into the grid
        Button hintButton = new Button(new ContextThemeWrapper(this, style.NumpadButtonStyle));
        hintButton.setText("Get hint");
        hintButton.setOnClickListener(v -> {
            if(selectedRow >= 0 && selectedCol >= 0) { //ensure a cell is selected
                //create a local copy of the grid so it can be solved
                int[][] cellGrid = new int[9][9];
                for (int i = 0; i < 9; i++)
                    for (int j = 0; j < 9; j++)
                        if (this.cells[i][j].getText().toString().isEmpty())
                            cellGrid[i][j] = 0;
                        else
                            cellGrid[i][j] = Integer.parseInt(this.cells[i][j].getText().toString());
                SudokuGrid sdgrid = new SudokuGrid(cellGrid);

                if(sdgrid.isEmpty()){ //edge case: empty grids can't be solved
                    outputText.setText("Grid is empty :/");
                    outputText.setTextColor(getResources().getColor(color.red));
                }else { //non-empty grid can be solved
                    Tuple<Boolean, SudokuGrid> output = SudokuGrid.solve(new SudokuGrid(cellGrid)); //solve the local copy
                    if (output.x) { //if it was correctly solved
                        int cell = output.y.getCell(selectedRow, selectedCol);
                        this.cells[selectedRow][selectedCol].setText(cell + "");
                        outputText.setText("");
                    } else {
                        outputText.setText("Grid is not solvable :(");
                        outputText.setTextColor(getResources().getColor(color.red));
                    }
                }
            }
        });
        numpadGrid.addView(hintButton);

        //this button solves the currently displayed sudoku grid
        Button solveButton = findViewById(id.solveButton);
        solveButton.setText("Solve");
        solveButton.setOnClickListener(v -> {
            //create local copy of grid
            int[][] cellGrid = new int[9][9];
            for(int i = 0; i < 9; i++)
                for(int j = 0; j < 9; j++)
                    if(this.cells[i][j].getText().toString().isEmpty())
                        cellGrid[i][j] = 0;
                    else
                        cellGrid[i][j] = Integer.parseInt(this.cells[i][j].getText().toString());
            SudokuGrid grid = new SudokuGrid(cellGrid);

            //(try to) solve grid and either set output grid or output message about not being solvable
            if(grid.isEmpty()){ //edge case: can't solve an empty grid
                outputText.setText("Grid is empty :/");
                outputText.setTextColor(getResources().getColor(color.red));
            }else {
                Tuple<Boolean, SudokuGrid> solvedOutput = SudokuGrid.solve(grid);
                if (solvedOutput.x) {
                    updateSudokuGrid(solvedOutput.y.getGrid());
                    outputText.setText("Grid solved successfully :)");
                    outputText.setTextColor(getResources().getColor(color.green));
                } else {
                    outputText.setText("Grid is not solvable :(");
                    outputText.setTextColor(getResources().getColor(color.red));
                }
            }
        });

        //this button clears the grid, i.e. sets all cells to 0 / empty
        Button clearGridButton = findViewById(id.clearButton);
        clearGridButton.setText("Clear grid");
        clearGridButton.setOnClickListener(v -> {
            //confirmation dialog to prevent accidental deletion
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Clear Grid");
            builder.setMessage("Are you sure you want to clear the Sudoku grid?");
            builder.setPositiveButton("Yes", (dialog, which) -> {
                int[][] cellGrid = new int[9][9];
                for(int i = 0; i < 9; i++)
                    for(int j = 0; j < 9; j++)
                        cellGrid[i][j] = 0;
                SudokuGrid grid = new SudokuGrid(cellGrid);
                updateSudokuGrid(grid.getGrid());
                outputText.setText("");
            });
            builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        });

        //this button opens / closes the camera. Also leads to the camera feed being displayed on top of the sudoku grid
        cameraButton = findViewById(id.cameraButton);
        cameraButton.setText("Open camera");
        cameraButton.setOnClickListener(v -> {
            //either enable or disable camera and previewview
            if(previewView.getVisibility() == View.VISIBLE){ //turn off camera
                previewView.setVisibility(View.GONE);
                cameraButton.setText("Open camera");
                cameraProviderFuture.addListener(() -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        cameraProvider.unbindAll();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }, ContextCompat.getMainExecutor(this));
            }else{ //turn on camera
                //check / request permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    //start camera
                    previewView.getLayoutParams().width = sudokuGrid.getWidth();
                    previewView.getLayoutParams().height = sudokuGrid.getHeight();
                    previewView.setVisibility(View.VISIBLE);
                    cameraButton.setText("Close camera");
                    setupCamera();
                } else {
                    //request permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }

        });


        //this button takes a picture when the camera is open and attempts to identify a sudoku grid / its cells using OCR
        Button takePictureButton = findViewById(id.takePictureButton);
        takePictureButton.setText("Take picture");
        takePictureButton.setOnClickListener(v -> takePicture());

        //initialize text recognizer
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);


        //useTestGrid();
    }


    /**
     * This method updates the displayed grid (sets the text of each cell button). 0 means empty, i.e. ""
     * @param grid the new grid to be displayed
     */
    void updateSudokuGrid(int[][] grid){
        for(int i = 0; i<9; i++)
            for(int j = 0; j<9; j++)
                if(grid[i][j] != 0)
                    this.cells[i][j].setText(String.valueOf(grid[i][j]));
                else
                    this.cells[i][j].setText("");
    }


    /**
     * This method checks if the cell belongs to the top, bottom, left or right boxes.
     * These boxes need to receive a different background color for design reasons.
     * @param x row
     * @param y column
     * @return true if it's in the top, bottom, left or right box, false if not
     */
    boolean needsSecondColor(int x, int y){
        return x >= 3 && x < 6 && y < 3
                || x >= 3 && x < 6 && y >= 6
                || y >= 3 && y < 6 && x < 3
                || y >= 3 && y < 6 && x >= 6;
    }


    //-----------------------------------------------------------------------------------------------
    //Image processing

    /**
     * this is used to get the camera functionality for scanning grids
     */
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    /**
     * use case for taking the grid picture
     */
    private ImageCapture imageCapture;

    /**
     * this element displays the camera feed on top of the sudoku grid when the camera is active
     */
    PreviewView previewView;

    /**
     * this is used to perform OCR on the grid cells from a picture
     */
    private TextRecognizer recognizer;

    /**
     * Sets up all the camera parameters required for taking pictures of grids when the camera is activated
     */
    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                //create Preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                //create ImageCapture use case
                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())  // Ensure correct orientation
                        .build();
                //bind use cases to the lifecycle
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Takes a picture and processes it
     * Processing entails: Conversion to bitmap, rotation, splitting into cells, OCR processing on each cell and finally inserting the recognized digits into the cells.
     */
    private void takePicture() {
        if (imageCapture == null) return; //nothing happens when the camera isn't active
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                //process image
                //logImageDetails(imageProxy);
                Bitmap bitmap = rotateBitmap(imageProxyToBitmap(imageProxy), imageProxy.getImageInfo().getRotationDegrees());
                processCells(splitIntoCells(cropToSquare(bitmap)));  // OCR processing

                //disable / close everything afterwards
                imageProxy.close();
                previewView.setVisibility(PreviewView.GONE);
                cameraButton.setText("Open camera");
                try {
                    cameraProviderFuture.get().unbindAll();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // Handle the error
                exception.printStackTrace();
            }
        });
    }


    /**
     * This method crops the image that was taken to the size of the grid so it can be split up into correctly sized cells
     * @param originalBitmap The bitmap to be cropped
     * @return The cropped, square-shaped bitmap
     */
    private Bitmap cropToSquare(Bitmap originalBitmap) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        // get square height/width (the smaller dimension from the original map)
        int newDimension = Math.min(width, height);

        //get starting points for cropping
        int xOffset = (width - newDimension) / 2;
        int yOffset = (height - newDimension) / 2;

        //return cropped bitmap
        return Bitmap.createBitmap(originalBitmap, xOffset, yOffset, newDimension, newDimension);
    }

    /**
     * Conversion method to get the bitmap that works with OCR from the original JPEG
     * @param imageProxy the picture taken from the camera, in JPEG format
     * @return the corresponding bitmap to be cropped and used in the OCR process
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        //extract jpeg data
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] jpegData = new byte[buffer.remaining()];
        buffer.get(jpegData);

        //convert byte array to Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        imageProxy.close();
        return bitmap;
    }

    /**
     * splits the original square-shaped bitmap describing the entire grid into its 81 cells
     * @param sudokuBitmap the original grid as a square-shaped bitmap
     * @return an array of 81 bitmaps, each describing a single cell
     */
    private Bitmap[] splitIntoCells(Bitmap sudokuBitmap) {
        int gridSize = 9; //sudoku grid is 9x9
        int cellWidth = sudokuBitmap.getWidth() / gridSize;
        int cellHeight = sudokuBitmap.getHeight() / gridSize;
        Bitmap[] cells = new Bitmap[gridSize * gridSize];

        //split image into individual cells
        for (int row = 0; row < gridSize; row++)
            for (int col = 0; col < gridSize; col++) {
                //get cell's position
                int xOffset = col * cellWidth;
                int yOffset = row * cellHeight;
                //extract bitmap for this cell
                cells[row * gridSize + col] = Bitmap.createBitmap(sudokuBitmap, xOffset, yOffset, cellWidth, cellHeight);
            }
        return cells;
    }

    /**
     * This method performs OCR and writes the recognized digits into the cell buttons
     * @param cellBitmaps The array of bitmaps, one bitmap for each of the 81 cells
     */
    private void processCells(Bitmap[] cellBitmaps) {
        int[][] grid = new int[9][9];
        for (int i = 0; i < cellBitmaps.length; i++) {
            InputImage image = InputImage.fromBitmap(cellBitmaps[i], 0);
            final int cellRow = i;
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String detectedDigit = visionText.getText().trim();
                        //Log.d("OCR", "successListener reached with detectedDigit == " + detectedDigit);
                        //Log.d("OCR", "successListener reached with detectedDigit.isEmpty() == " + detectedDigit.isEmpty());
                        if (!detectedDigit.isEmpty() && Character.isDigit(detectedDigit.charAt(0))) {
                            int digit = Character.getNumericValue(detectedDigit.charAt(0));
                            if(digit != 0) { //ensures only valid digits 1-9 are entered into the grid
                                //get row and column from the index
                                int row = cellRow / 9;
                                int col = cellRow % 9;
                                //Log.d("OCR", "Identified digit at cell (" + row + "," + col + "): " + digit);
                                //insert recognized digit into the cell
                                grid[row][col] = digit;
                                cells[row][col].setText("" + digit);
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e("OCR", "Error processing cell " + cellRow + ": " + e.getMessage()));
        }
        updateSudokuGrid(grid);
    }

    /**
     * This rotates the bitmap to ensure that the grid is correctly parsed as it is displayed in the preview during picture-taking
     * @param bitmap the original bitmap, possibly with incorrect orientation
     * @param rotationDegrees the degrees used to correct the orientation
     * @return the rotated bitmap
     */
    private Bitmap rotateBitmap(Bitmap bitmap, float rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    //-----------------------------------------------------------------------------------------------
    //debug methods

    /**
     * just for debugging
     * @param imageProxy
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void logImageDetails(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            Log.d("ImageDetails", "Format: " + imageProxy.getImage().getFormat());
            Log.d("ImageDetails", "Number of planes: " + imageProxy.getPlanes().length);
            for (int i = 0; i < planes.length; i++)
                Log.d("ImageDetails", "Plane " + i + " buffer size: " + planes[i].getBuffer().capacity());
        }
    }

    /**
     * Loads a test grid for debugging
     */
    void useTestGrid(){

        int[][] testSudokuGridOld = {
                {5, 3, 4, 6, 7, 8, 9, 1, 2},
                {6, 7, 2, 1, 9, 5, 3, 4, 8},
                {1, 9, 8, 3, 4, 2, 5, 6, 7},
                {8, 5, 9, 7, 6, 1, 4, 2, 3},
                {4, 2, 6, 8, 5, 3, 7, 9, 1},
                {7, 1, 3, 9, 2, 4, 8, 5, 6},
                {9, 6, 1, 5, 3, 7, 2, 8, 4},
                {2, 8, 7, 4, 1, 9, 6, 3, 5},
                {3, 4, 5, 2, 8, 6, 1, 7, 9}
        };

        int[][] testSudokuGrid = {
                {5, 3, 0, 0, 7, 0, 0, 0, 0},
                {6, 0, 0, 1, 9, 5, 0, 0, 0},
                {0, 9, 8, 0, 0, 0, 0, 6, 0},
                {8, 0, 0, 0, 6, 0, 0, 0, 3},
                {4, 0, 0, 8, 0, 3, 0, 0, 1},
                {7, 0, 0, 0, 2, 0, 0, 0, 6},
                {0, 6, 0, 0, 0, 0, 2, 8, 0},
                {0, 0, 0, 4, 1, 9, 0, 0, 5},
                {0, 0, 0, 0, 8, 0, 0, 7, 9}
        };
        updateSudokuGrid(testSudokuGrid);
    }

}

/**
 * This class is a set of vertical and horizontal lines to be displayed on top of the sudoku grid or camera feed
 * It's a separate component so it can be overlayed on both elements to make the UI more intuitive
 */
class SudokuGridOverlay extends View {

    private Paint paint;

    public SudokuGridOverlay(Context context) {
        super(context);
        init();
    }

    public SudokuGridOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        //initialize paint object for the lines
        paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int cellSize = getWidth() / 9;
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(5);

        //horizontal lines
        for (int i = 0; i <= 9; i++) {
            int position = i * cellSize;
            if (position <= getHeight()) //ensure line is within bounds
                canvas.drawLine(0, position, getWidth(), position, paint);
        }
        //vertical lines
        for (int i = 0; i <= 9; i++) {
            int position = i * cellSize;
            if (position <= getWidth()) //ensure line is within bounds
                canvas.drawLine(position, 0, position, getHeight(), paint);
        }
    }
}