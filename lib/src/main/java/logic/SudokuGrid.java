package logic;


import java.util.HashSet;
import java.util.Set;

/**
 * This is the representation for our Sudoku grid
 */
public class SudokuGrid {

    /**
     * The main sudoku grid. Empty cells are represented as 0.
     */
    int[][] grid;
    public int[][] getGrid(){
        return this.grid;
    }
    public int getCell(int x, int y){
        return this.grid[x][y];
    }
    public int getCell(Point xy){
        return this.grid[xy.x][xy.y];
    }
    public void setCell(Point xy, int value){
        this.grid[xy.x][xy.y] = value;
    }

    /**
     * This is a set containing a list of Points (x,y) describing the cells contained in each box (each box has its own set of points)
     */
    Set<Point>[] boxIndexes;

    /**
     * This is a list of all the digits that could fit into the corresponding grid cell. Used for the LRV heuristic.
     */
    Set<Integer>[][] possibleDigits;

    /**
     * SudokuGrid constructor, creates a grid using the integer grid
     * @param grid: grid[rows=9][columns=9], integer array containing digits for each cell (or 0 for empty cells)
     */
    public SudokuGrid(int[][] grid){
        this.grid = grid;
        this.possibleDigits = new HashSet[9][9];
        this.boxIndexes = new HashSet[9];
        //create box index
        for(int i = 0; i<3; i++) //iterate over box rows
            for(int j = 0; j<3; j++){ //iterate over box columns within that row
                //starting cell (top left cell): i*3, j*3
                boxIndexes[(i*3+j)] = new HashSet<Point>(); //the current box. E.g.: the fourth box is the first in the second row, i.e., i=1 and j=0
                for(int k = 0; k<3; k++) //iterate over rows within the box
                    for(int l = 0; l<3; l++)
                        boxIndexes[(i*3+j)].add(new Point(i*3 + k, j*3 + l));
            }
        //initialize the set of possible digits. if it's occupied, leave it empty. if it's not, assume all digits are possible
        for(int i = 0; i<9; i++)
            for(int j = 0; j<9; j++){
                this.possibleDigits[i][j] = new HashSet<Integer>();
                if(this.getCell(i,j) == 0){
                    this.possibleDigits[i][j] = new HashSet<Integer>();
                    this.possibleDigits[i][j].add(1);
                    this.possibleDigits[i][j].add(2);
                    this.possibleDigits[i][j].add(3);
                    this.possibleDigits[i][j].add(4);
                    this.possibleDigits[i][j].add(5);
                    this.possibleDigits[i][j].add(6);
                    this.possibleDigits[i][j].add(7);
                    this.possibleDigits[i][j].add(8);
                    this.possibleDigits[i][j].add(9);
                }
            }
        //now initialize the "real" possible digits
        for(int i = 0; i<9; i++)
            for(int j = 0; j<9; j++)
                if(this.getCell(i,j) != 0)
                    updateAffectedDigits(new Point(i,j));
    }

    /**
     * This constructor is used to deep-copy existing SudokuGrids to avoid ConcurrentModificationExceptions
     * @param sdgrid the grid to be copied
     */
    public SudokuGrid(SudokuGrid sdgrid) {
        this.grid = new int[sdgrid.grid.length][sdgrid.grid[0].length];
        for (int i = 0; i < sdgrid.grid.length; i++) {
            System.arraycopy(sdgrid.grid[i], 0, this.grid[i], 0, sdgrid.grid[i].length);
        }
        this.boxIndexes = new Set[sdgrid.boxIndexes.length];
        for (int i = 0; i < sdgrid.boxIndexes.length; i++)
            this.boxIndexes[i] = new HashSet<>(sdgrid.boxIndexes[i]);
        this.possibleDigits = new Set[sdgrid.possibleDigits.length][sdgrid.possibleDigits[0].length];
        for (int i = 0; i < sdgrid.possibleDigits.length; i++)
            for (int j = 0; j < sdgrid.possibleDigits[i].length; j++)
                this.possibleDigits[i][j] = new HashSet<>(sdgrid.possibleDigits[i][j]);
    }

    /**
     * Solves SudokuGrids using a DFS backtracking search with LRV (least remaining values) heuristic
     * @return Tuple(true,solvedGrid) if solved correctly, Tuple(false,null) if not solvable.
     */
    public static Tuple<Boolean, SudokuGrid> solve(SudokuGrid sdgrid) {
        sdgrid.updateAllPossibleDigits();
        Point nextCell = sdgrid.findCellWithFewestOptions(); //choose next cell
        if (nextCell == null)
            if(sdgrid.containsNoDuplicates())
                return new Tuple<>(true, sdgrid); //solved the grid
            else
                return new Tuple<>(false, null);

        Set<Integer> options = sdgrid.possibleDigits[nextCell.x][nextCell.y];
        if (options.isEmpty()) {
            return new Tuple<>(false, null);
        }
        for (int i : options) {
            SudokuGrid sdgridcopy = new SudokuGrid(sdgrid); // Deep copy to avoid side effects
            sdgridcopy.setCell(nextCell, i);
            boolean isValid = sdgridcopy.updateAffectedDigits(nextCell);
            if (!isValid) continue;
            Tuple<Boolean, SudokuGrid> result = solve(sdgridcopy);
            if (result.x) return result;
        }
        return new Tuple<>(false, null);
    }

    /**
     * This method returns the first cell it finds that has the fewest possible options left, used for the LRV heuristic.
     * @return One of the cells with the fewest options left.
     */
    private Point findCellWithFewestOptions() {
        for(int options = 1; options<9; options++)
            for(int i = 0; i<9; i++)
                for(int j = 0; j<9; j++)
                    if(this.possibleDigits[i][j].size() == options)
                        return new Point(i,j);
        return null;
    }

    /**
     * Helper method for debugging, prints the current grid
     */
    public void printGrid(){
        System.out.println("-------------------------------");
        for(int i = 0; i<9; i++){ //print each row
            System.out.print("|");
            for(int j = 0; j<9; j++){
                if(j == 3 || j == 6)
                    System.out.print("|");
                if(this.getCell(i,j) == 0)
                    System.out.print("   ");
                else
                    System.out.print(" " + this.getCell(i,j) + " ");
            }
            System.out.println("|");
            if(i == 2 || i == 5)
                System.out.println("-------------------------------");
        }
        System.out.println("-------------------------------");
    }

    /**
     * This method updates the possible values for each cell that would be affected by a change in the (x,y) cell (x row, y column, the box (x,y) belongs to)
     * @param p the coordinates of the cell whose impact needs to be checked
     * @return true if there are no issues, false if there is now a cell with no possible values
     */
    public boolean updateAffectedDigits(Point p){
        int x = p.x;
        int y = p.y;
        int currentDigit = this.getCell(x,y);
        this.possibleDigits[x][y] = new HashSet<>();
        this.possibleDigits[x][y].add(currentDigit);
        //check rows and columns
        for (int i = 0; i<9; i++){
            if(this.getCell(x,i) == 0){
                this.possibleDigits[x][i].remove(currentDigit);
                if(this.possibleDigits[x][i].isEmpty())
                    return false;
            }
            if(this.getCell(i,y) == 0){
                this.possibleDigits[i][y].remove(currentDigit);
                if(this.possibleDigits[i][y].isEmpty())
                    return false;
            }
        }
        //check box
        for(Set<Point> boxIndexes : this.boxIndexes)
            if (boxIndexes.contains(new Point(x,y))) //this means we arrived at the correct box and now need to check the cells in that box
                for(Point cell : boxIndexes)
                    if(this.getCell(cell) == 0) {
                        this.possibleDigits[cell.x][cell.y].remove(currentDigit);
                        if(this.possibleDigits[cell.x][cell.y].isEmpty())
                            return false;
                    }

        return this.containsNoDuplicates();
    }

    /**
     * Checks if the current grid contains errors (e.g., duplicates within rows, columns or boxes (the 3x3 subgrids))
     * @return false if the grid contains duplicates and is invalid, true if the grid doesn't contain duplicates
     */
    public boolean containsNoDuplicates(){
        //iterate through digits 1-9
        for(int digit = 1; digit<=9; digit++){ //check rows, columns and boxes for each digit
            //row duplicates
            for(int i = 0; i<9; i++){
                int counter = 0;
                for(int j = 0; j<9; j++)
                    if(this.getCell(i, j) == digit){
                        counter++;
                        if(counter >= 2)
                            return false;
                    }
            }
            //column duplicates
            for(int j = 0; j<9; j++){
                int counter = 0;
                for(int i = 0; i<9; i++)
                    if(this.getCell(i, j) == digit){
                        counter++;
                        if(counter >= 2)
                            return false;
                    }
            }
            //box duplicates
            for(Set<Point> boxIndexes : this.boxIndexes) {
                int counter = 0;
                for (Point cell : boxIndexes)
                    if(this.getCell(cell) == digit){
                        counter++;
                        if(counter >= 2)
                            return false;
                    }
            }
        }
        return true;
    }

    /**
     * This iterates through all grid cells and crosses off values that aren't possible to insert anymore (because they'd be duplicates).
     */
    public void updateAllPossibleDigits() {
        for(int i = 0; i<9; i++)
            for(int j = 0; j<9; j++)
                this.updatePossibleDigits(i,j);
    }

    /**
     * This updates the set of possible digits that could fit into cell (x,y)
     * @param x,y: coordinates of the cell to be updated
     */
    public void updatePossibleDigits(int x, int y){
        int currentDigit = this.getCell(x,y);
        //if the cell is already occupied, remove everything except that digit
        if(this.getCell(x,y) != 0) {
            this.possibleDigits[x][y] = new HashSet<>();
            this.possibleDigits[x][y].add(currentDigit);
        }
        //check rows and columns
        for (int i = 0; i<9; i++){
            if(this.getCell(x,i) != 0)
                this.possibleDigits[x][y].remove(this.getCell(x,i));
            if(this.getCell(i,y) != 0)
                this.possibleDigits[i][y].remove(this.getCell(i,y));
        }
        //check box
        for(Set<Point> boxIndexes : this.boxIndexes)
            if (boxIndexes.contains(new Point(x,y))) //this means we arrived at the correct box and now need to check the cells in that box
                for(Point cell : boxIndexes)
                    if(this.getCell(cell) != 0)
                        this.possibleDigits[x][y].remove(this.getCell(cell));
    }

    /**
     * checks if the Sudoku grid is empty, i.e. all cells are 0
     * @return true if it's empty, false if not
     */
    public boolean isEmpty() {
        for(int i = 0; i<9; i++)
            for (int j = 0; j<9; j++)
                if(this.getCell(i,j) != 0)
                    return false;
        return true;
    }
}

