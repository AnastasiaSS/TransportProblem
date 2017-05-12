package Main;

/**
 * Created by Настя on 27.04.2017.
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;

public class Main {
    private Double[] demand;
    private Double[] supply;
    private double[][] costs;
    private Shipment[][] matrix;
    private String fileName;
    private JLabel label;

    class Shipment {
        final double costPerUnit;
        final int r, c;
        double quantity;

        public Shipment(double q, double cpu, int r, int c) {
            quantity = q;
            costPerUnit = cpu;
            this.r = r;
            this.c = c;
        }
    }
    private ArrayList<Double> convertToDouble(String[] words){
        ArrayList<Double> res=new ArrayList<Double>();
        for (int i=0; i<words.length; i++){
            String s=words[i].replace(',', '.');
            res.add(Double.parseDouble(s));
        }
        return res;
    }
    private void init() throws Exception {

        try{
            BufferedReader reader=new BufferedReader(new FileReader(fileName));
            String s=reader.readLine();
            int size=0, index=0;
            List<Double> src=new ArrayList<Double>();
            List<Double> dst=new ArrayList<Double>();
            List<ArrayList<Double>>  table=new ArrayList<ArrayList<Double>>();
            if(s!=null){
                String[] temp=s.split("  *");
                size=temp.length;
                ArrayList<Double> array=convertToDouble(temp);
                src.add(array.get(array.size()-1));
                array.remove(array.size()-1);
                table.add(index, array);
                index++;
            }
            while ((s=reader.readLine())!=null){
                String[] temp=s.split("  *");
                ArrayList<Double> array=convertToDouble(temp);
                if(size==temp.length){
                    src.add(array.get(array.size()-1));
                    array.remove(array.size()-1);
                    table.add(index, array);
                    index++;
                }
                else
                    dst.addAll(array);
            }
            reader.close();
            int numSources=src.size();
            int numDestinations=dst.size();
            // fix imbalance
            double totalSrc=0, totalDst=0;
            for(Double f: src)
                totalSrc+=f;
            for(Double f: dst)
                totalDst+=f;
            if (totalSrc > totalDst)
                dst.add(totalSrc - totalDst);
            else if (totalDst > totalSrc)
                src.add(totalDst - totalSrc);
            supply=new Double[src.size()];
            demand=new Double[dst.size()];
            supply = src.toArray(supply);
            demand = dst.toArray(demand);

            costs = new double[supply.length][demand.length];
            matrix = new Shipment[supply.length][demand.length];

            for (int i = 0; i < numSources; i++)
                for (int j = 0; j < numDestinations; j++)
                    costs[i][j] = table.get(i).get(j);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void northWestCornerRule() {

        for (int r = 0, northwest = 0; r < supply.length; r++)
            for (int c = northwest; c < demand.length; c++) {

                double quantity = Math.min(supply[r], demand[c]);
                if (quantity > 0) {
                    matrix[r][c] = new Shipment(quantity, costs[r][c], r, c);

                    supply[r] -= quantity;
                    demand[c] -= quantity;

                    if (supply[r] == 0) {
                        northwest = c;
                        break;
                    }
                }
            }
    }

    private void steppingStone() {
        double maxReduction = 0;
        Shipment[] move = null;
        Shipment leaving = null;

        fixDegenerateCase();

        for (int r = 0; r < supply.length; r++) {
            for (int c = 0; c < demand.length; c++) {

                if (matrix[r][c] != null)
                    continue;

                Shipment trial = new Shipment(0, costs[r][c], r, c);
                Shipment[] path = getClosedPath(trial);

                double reduction = 0;
                double lowestQuantity = Integer.MAX_VALUE;
                Shipment leavingCandidate = null;

                boolean plus = true;
                for (Shipment s : path) {
                    if (plus) {
                        reduction += s.costPerUnit;
                    } else {
                        reduction -= s.costPerUnit;
                        if (s.quantity < lowestQuantity) {
                            leavingCandidate = s;
                            lowestQuantity = s.quantity;
                        }
                    }
                    plus = !plus;
                }
                if (reduction < maxReduction) {
                    move = path;
                    leaving = leavingCandidate;
                    maxReduction = reduction;
                }
            }
        }

        if (move != null) {
            double q = leaving.quantity;
            boolean plus = true;
            for (Shipment s : move) {
                s.quantity += plus ? q : -q;
                matrix[s.r][s.c] = s.quantity == 0 ? null : s;
                plus = !plus;
            }
            steppingStone();
        }
    }

    private LinkedList<Shipment> matrixToList() {
        return stream(matrix)
                .flatMap(row -> stream(row))
                .filter(s -> s != null)
                .collect(toCollection(LinkedList::new));
    }

    private Shipment[] getClosedPath(Shipment s) {
        LinkedList<Shipment> path = matrixToList();
        path.addFirst(s);

        // remove (and keep removing) elements that do not have a
        // vertical AND horizontal neighbor
        while (path.removeIf(e -> {
            Shipment[] nbrs = getNeighbors(e, path);
            return nbrs[0] == null || nbrs[1] == null;
        }));

        // place the remaining elements in the correct plus-minus order
        Shipment[] stones = path.toArray(new Shipment[path.size()]);
        Shipment prev = s;
        for (int i = 0; i < stones.length; i++) {
            stones[i] = prev;
            prev = getNeighbors(prev, path)[i % 2];
        }
        return stones;
    }

    private Shipment[] getNeighbors(Shipment s, LinkedList<Shipment> lst) {
        Shipment[] nbrs = new Shipment[2];
        for (Shipment o : lst) {
            if (o != s) {
                if (o.r == s.r && nbrs[0] == null)
                    nbrs[0] = o;
                else if (o.c == s.c && nbrs[1] == null)
                    nbrs[1] = o;
                if (nbrs[0] != null && nbrs[1] != null)
                    break;
            }
        }
        return nbrs;
    }

    private void fixDegenerateCase() {
        final double eps = Double.MIN_VALUE;

        if (supply.length + demand.length - 1 != matrixToList().size()) {

            for (int r = 0; r < supply.length; r++)
                for (int c = 0; c < demand.length; c++) {
                    if (matrix[r][c] == null) {
                        Shipment dummy = new Shipment(eps, costs[r][c], r, c);
                        if (getClosedPath(dummy).length == 0) {
                            matrix[r][c] = dummy;
                            return;
                        }
                    }
                }
        }
    }

    public void work(){
        JFrame frame=new JFrame("Transport problem");
        JPanel mainPanel=new JPanel();

        JButton buttonSolve=new JButton("Solve");
        buttonSolve.addActionListener(new Main.SolveListener());
        mainPanel.add(buttonSolve);

        label=new JLabel();
        mainPanel.add(label);
        //form menu
        JMenuBar menuBar=new JMenuBar();
        JMenu fileMenu=new JMenu("Choose file");
        JMenuItem nMenuItem=new JMenuItem("New");
        nMenuItem.addActionListener(new Main.NewMenuListener());

        fileMenu.add(nMenuItem);
        menuBar.add(fileMenu);

        frame.setJMenuBar(menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, mainPanel);
        frame.setSize(300,120);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }
    public class NewMenuListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event){
            JFileChooser fileopen = new JFileChooser();
            int ret = fileopen.showDialog(null, "Открыть файл");
            if (ret == JFileChooser.APPROVE_OPTION) {
                fileName=fileopen.getSelectedFile().getPath();
                label.setText(" ");
            }
        }
    }
    public class SolveListener implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent event){
            try {
                if(fileName!=null) {
                    init();
                    northWestCornerRule();
                    steppingStone();
                    printResult();
                }
                else{
                    label.setText("ERROR - CHOOSE FILE");
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    private void printResult() {
        double totalCosts = 0;
        String[][] resTab=new String[supply.length+1][demand.length+1];
        resTab[0][0]="  ";
        for(int i=1; i<demand.length+1; i++)
            resTab[0][i]="Shop"+i;
        for (int r = 0; r < supply.length; r++) {
            resTab[r+1][0]="Factory"+(r+1);
            for (int c = 0; c < demand.length; c++) {
                Shipment s = matrix[r][c];
                if (s != null && s.r == r && s.c == c) {
                    resTab[r+1][c+1]=""+s.quantity;
                    totalCosts += (s.quantity * s.costPerUnit);
                } else {
                    resTab[r+1][c+1]="-";
                }
            }
        }
        String[] columns=new String[demand.length+1];
        for(int i=0; i<demand.length+1; i++){
            columns[i]=""+i;
        }
        JTable table=new JTable(resTab, columns);
        JFrame myFrame=new JFrame("Solution");
        JLabel solutionLabel=new JLabel("Optional solution ");
        JLabel costsLabel=new JLabel("Total costs: "+totalCosts);
        JPanel panel=new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(solutionLabel);
        panel.add(table);
        panel.add(costsLabel);
        myFrame.getContentPane().add(BorderLayout.CENTER, panel);
        myFrame.setMaximumSize(new Dimension(2000, 2000));
        myFrame.pack();
        myFrame.setLocationRelativeTo(null);
        myFrame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {

        Main solution = new Main();
        solution.work();
    }
}
