package com.mahmoodms.bluetooth.emghandcontrol;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;

/**
 * Created by mahmoodms on 5/15/2017.
 */

public class GraphAdapter {
    // Variables
    private boolean filterData;
    public int intArraySize;
    SimpleXYSeries series;
    LineAndPointFormatter lineAndPointFormatter;
    private int seriesHistoryDataPoints;
    double[] lastTimeValues;
    double[] unfilteredSignal;
    boolean plotData;

    // Set/Get Methods (Don't need yet)
    public void setPlotData(boolean plotData) {
        this.plotData = plotData;
        if (!plotData) {
            clearPlot();
        }
    }

    // Constructor
    public GraphAdapter(int seriesHistoryDataPoints, String XYSeriesTitle, boolean useImplicitXVals, boolean filterData, int lineAndPointFormatterColor) {
        //default values
        this.filterData = filterData;
        this.seriesHistoryDataPoints = seriesHistoryDataPoints;
        this.intArraySize = 6; //24-bit default
        this.lineAndPointFormatter = new LineAndPointFormatter(lineAndPointFormatterColor, null, null, null);
        setPointWidth(5); //Def value:
        //Initialize arrays:
        this.unfilteredSignal = new double[seriesHistoryDataPoints];
        // Initialize series
        this.series = new SimpleXYSeries(XYSeriesTitle);
        if(useImplicitXVals) this.series.useImplicitXVals();
        //Don't plot data until explicitly told to do so:
        this.plotData = false;
    }

    public void setPointWidth(float width) {
        this.lineAndPointFormatter.getLinePaint().setStrokeWidth(width);
    }

    public void addDataPoint(double data, int index) {
        if(this.plotData) plot((double)index*0.004,data);
    }

    //Graph Stuff:
    private void clearPlot() {
        if(this.series!=null) {
            DeviceControlActivity.redrawer.pause();
            while(this.series.size()>0) {
                this.series.removeFirst();
            }
            DeviceControlActivity.redrawer.start();
        }
    }

    private void plot(double x, double y) {
        if(series.size()>seriesHistoryDataPoints-1) {
            series.removeFirst();
        }
        series.addLast(x,y);
    }
}
