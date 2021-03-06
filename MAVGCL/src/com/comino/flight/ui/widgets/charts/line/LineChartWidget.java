/****************************************************************************
 *
 *   Copyright (c) 2017 - 2019 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.charts.line;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.AnalysisDataModelMetaData;
import com.comino.flight.model.KeyFigureMetaData;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.model.service.ICollectorRecordingListener;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.prefs.MAVPreferences;
import com.comino.flight.ui.widgets.charts.IChartControl;
import com.comino.flight.ui.widgets.charts.IChartSyncControl;
import com.comino.flight.ui.widgets.charts.annotations.DashBoardAnnotation;
import com.comino.flight.ui.widgets.charts.annotations.LineMessageAnnotation;
import com.comino.flight.ui.widgets.charts.annotations.ModeAnnotation;
import com.comino.flight.ui.widgets.charts.utils.XYDataPool;
import com.comino.jfx.extensions.MovingAxis;
import com.comino.jfx.extensions.SectionLineChart;
import com.comino.jfx.extensions.XYAnnotations.Layer;
import com.comino.mav.control.IMAVController;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart.SortingPolicy;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class LineChartWidget extends BorderPane implements IChartControl, ICollectorRecordingListener, IChartSyncControl {

	private final static int MAXRECENT 	    = 20;
	private final static int REFRESH_RATE   = 50;
	private final static int REFRESH_SLOT   = 20;

	private final static String[] BCKGMODES = { "No mode annotation ", "FlightMode","EKF2 Status", "Position estimation", "GPS fixtype" };

	@FXML
	private SectionLineChart<Number, Number> linechart;

	@FXML
	private MovingAxis xAxis;

	@FXML
	private NumberAxis yAxis;

	@FXML
	private ChoiceBox<String> group;

	@FXML
	private ChoiceBox<KeyFigureMetaData> cseries1;

	@FXML
	private ChoiceBox<KeyFigureMetaData> cseries2;

	@FXML
	private ChoiceBox<KeyFigureMetaData> cseries3;

	@FXML
	private Button export;

	@FXML
	private CheckBox annotations;

	@FXML
	private CheckBox dash;

	@FXML
	private ChoiceBox<String> bckgmode;

	@FXML
	private HBox bckglegend;

	private int id = 0;
	private int refresh_step = 0;

	private  XYChart.Series<Number,Number> series1;
	private  XYChart.Series<Number,Number> series2;
	private  XYChart.Series<Number,Number> series3;

	private KeyFigureMetaData type1 = null;
	private KeyFigureMetaData type2=  null;
	private KeyFigureMetaData type3=  null;

	private StateProperties state = null;

	private IntegerProperty timeFrame    = new SimpleIntegerProperty(30);
	private FloatProperty   scroll       = new SimpleFloatProperty(0);
	private FloatProperty   replay       = new SimpleFloatProperty(0);
	private BooleanProperty isScrolling  = new SimpleBooleanProperty(false);

	private int resolution_ms 	  = 100;

	private int current_x_pt      = 0;

	private int current_x0_pt     = 0;
	private int current_x1_pt     = 0;

	private AnalysisDataModelMetaData meta = AnalysisDataModelMetaData.getInstance();
	private AnalysisModelService      dataService = AnalysisModelService.getInstance();

	private ArrayList<KeyFigureMetaData> recent = null;

	private Gson  gson = new GsonBuilder().create();
	private int   yoffset = 0;
	private int   last_annotation_pos = 0;

	private double x;
	private float timeframe;
	private boolean display_annotations = true;
	private boolean isPaused = false;

	private DashBoardAnnotation dashboard1 = null;
	private DashBoardAnnotation dashboard2 = null;
	private DashBoardAnnotation dashboard3 = null;
	private ModeAnnotation            mode = null;

	private List<IChartSyncControl> syncCharts = null;

	private XYDataPool pool = null;

	private Preferences prefs = MAVPreferences.getInstance();

	private boolean refreshRequest = false;
	private boolean isRunning = false;

	private long dashboard_update_tms = 0;
	private long last_update_ms = 0;

	public LineChartWidget() {

		this.syncCharts = new ArrayList<IChartSyncControl>();
		syncCharts.add(this);

		refresh_step = REFRESH_RATE / dataService.getCollectorInterval_ms();

		FXMLLoadHelper.load(this, "LineChartWidget.fxml");

		this.state = StateProperties.getInstance();
		this.pool  = new XYDataPool();

		dataService.registerListener(this);
	}


	@Override
	public void update(long now) {
		if(!isRunning || isDisabled() || !isVisible() )
			return;
		if((System.currentTimeMillis()-last_update_ms)> 25 || resolution_ms <= 100) {
			Platform.runLater(() -> {
				updateGraph(refreshRequest,0);
				last_update_ms = System.currentTimeMillis();
			});
			//	last_update_ms = System.currentTimeMillis();
		}
	}

	@FXML
	private void initialize() {

		mode       = new ModeAnnotation(bckglegend);

		bckgmode.getItems().addAll(BCKGMODES);
		bckgmode.getSelectionModel().select(0);

		linechart.setAxisSortingPolicy(SortingPolicy.NONE);
		linechart.setBackground(null);
		linechart.setCreateSymbols(false);

		series1 = new XYChart.Series<Number,Number>();
		linechart.getData().add(series1);
		series2 = new XYChart.Series<Number,Number>();
		linechart.getData().add(series2);
		series3 = new XYChart.Series<Number,Number>();
		linechart.getData().add(series3);

		annotations.setSelected(true);
		annotations.selectedProperty().addListener((observable, oldvalue, newvalue) -> {
			updateRequest();
		});

		dashboard1 = new DashBoardAnnotation(10);
		dashboard2 = new DashBoardAnnotation(90);
		dashboard3 = new DashBoardAnnotation(170);

		linechart.getAnnotations().add(mode, Layer.BACKGROUND);

		current_x1_pt = timeFrame.intValue() * 1000 / dataService.getCollectorInterval_ms();

		xAxis.setAutoRanging(false);
		xAxis.setLowerBound(0);
		xAxis.setUpperBound(timeFrame.intValue());
		xAxis.setLabel("Seconds");
		xAxis.setAnimated(false);
		xAxis.setCache(true);
		xAxis.setCacheHint(CacheHint.SPEED);

		yAxis.setForceZeroInRange(false);
		yAxis.setAutoRanging(true);
		yAxis.setPrefWidth(40);
		yAxis.setAnimated(false);
		yAxis.setCache(true);
		yAxis.setCacheHint(CacheHint.SPEED);

		linechart.setAnimated(false);
		linechart.setLegendVisible(true);
		linechart.setLegendSide(Side.TOP);
		//		linechart.setCache(true);
		//		linechart.setCacheHint(CacheHint.SPEED);

		linechart.prefWidthProperty().bind(widthProperty());
		linechart.prefHeightProperty().bind(heightProperty());

		Group chartArea = (Group)linechart.getAnnotationArea();
		final Rectangle zoom = new Rectangle();
		zoom.setStrokeWidth(0);
		chartArea.getChildren().add(zoom);
		zoom.setFill(Color.color(0,0.6,1.0,0.1));
		zoom.setVisible(false);
		zoom.setY(0);
		zoom.setHeight(1000);

		Label zoom_label = new Label();
		zoom_label.setStyle("-fx-font-size: 6pt;-fx-text-fill: #9090D0; -fx-padding:3;");
		zoom_label.setVisible(false);
		chartArea.getChildren().add(zoom_label);

		linechart.setOnMousePressed(mouseEvent -> {
			if((dataService.isCollecting() && !isPaused) || (dataService.isReplaying() && !isPaused)) {
				mouseEvent.consume();
				return;
			}

			x = mouseEvent.getX();
			zoom.setX(x-chartArea.getLayoutX()-7);

			zoom.setVisible(true);
			zoom.setWidth(1);


			int x1 = dataService.calculateXIndexByTime(xAxis.getValueForDisplay(mouseEvent.getX()-xAxis.getLayoutX()).doubleValue());
			if(x1 > 0) {
				dashboard1.setVal(dataService.getModelList().get(x1).getValue(type1),type1, true);
				dashboard2.setVal(dataService.getModelList().get(x1).getValue(type2),type2, true);
				dashboard3.setVal(dataService.getModelList().get(x1).getValue(type3),type3, true);
			}

			if(!dataService.isCollecting() && !dataService.isReplaying()) {
				dataService.setCurrent(x1);
				state.getCurrentUpToDate().set(false);
			}

			linechart.getPlotArea().requestLayout();
			mouseEvent.consume();
		});

		linechart.setOnMouseReleased(mouseEvent -> {
			if((dataService.isCollecting() && !isPaused) || (dataService.isReplaying() && !isPaused)) {
				mouseEvent.consume();
				return;
			}
			dashboard1.setVal(0,null,false);
			dashboard2.setVal(0,null,false);
			dashboard3.setVal(0,null,false);
			state.getCurrentUpToDate().set(true);
			linechart.setCursor(Cursor.DEFAULT);
			zoom.setVisible(false);
			zoom_label.setVisible(false);
			double x0 = xAxis.getValueForDisplay(x-xAxis.getLayoutX()).doubleValue() ;
			double x1 = xAxis.getValueForDisplay(mouseEvent.getX()-xAxis.getLayoutX()).doubleValue();

			if((x1-x0) > 0.2f)
				for(IChartSyncControl sync : syncCharts)
					sync.setZoom(x0, x1);

			mouseEvent.consume();
		});

		linechart.setOnMouseClicked(click -> {

			if (click.getClickCount() == 2) {
				for(IChartSyncControl sync : syncCharts)
					sync.returnToOriginalTimeScale();
				click.consume();
			}

		});

		linechart.setOnMouseDragged(mouseEvent -> {
			if((dataService.isCollecting() && !isPaused) || (dataService.isReplaying() && !isPaused)) {
				mouseEvent.consume();
				return;
			}

			if(type1.hash!=0 || type2.hash!=0 || type3.hash!=0) {
				zoom.setVisible(true);
				double xt0 = xAxis.getValueForDisplay(x-xAxis.getLayoutX()).doubleValue();
				double dtx = xAxis.getValueForDisplay(mouseEvent.getX() -xAxis.getLayoutX()).doubleValue() - xt0;
				int x0 = dataService.calculateXIndexByTime(xt0);
				int x1 = dataService.calculateXIndexByTime(xt0+dtx);

				dashboard1.setVal(dataService.getModelList().get(x1).getValue(type1),type1, true);
				dashboard2.setVal(dataService.getModelList().get(x1).getValue(type2),type2, true);
				dashboard3.setVal(dataService.getModelList().get(x1).getValue(type3),type3, true);

				if(!dataService.isCollecting() && !dataService.isReplaying()) {
					dataService.setCurrent(x1);
					state.getCurrentUpToDate().set(false);
				}

				if((mouseEvent.getX()-x)>0) {
					linechart.setCursor(Cursor.H_RESIZE);
					zoom.setWidth(mouseEvent.getX()-x);
					if((System.currentTimeMillis()-dashboard_update_tms)>200) {
						if((mouseEvent.getX() - x)> 30) {
							zoom_label.setVisible(true);
							zoom_label.setText(String.format("%#.1fs", dtx));
							zoom_label.setLayoutX(x-xAxis.getLayoutX());
						} else
							zoom_label.setVisible(false);

						setDashboardData(dashboard1,type1,x0,x1);
						setDashboardData(dashboard2,type2,x0,x1);
						setDashboardData(dashboard3,type3,x0,x1);

						dashboard_update_tms = System.currentTimeMillis();
					}
				}
				else {
					if((mouseEvent.getX()-x)<-5) {
						linechart.setCursor(Cursor.DEFAULT);
						zoom.setWidth(1);
					}
					zoom.setX(mouseEvent.getX()-chartArea.getLayoutX()-7);
				}
				linechart.getPlotArea().requestLayout();
			}
			mouseEvent.consume();
		});

		readRecentList();

		type1 = type2 = type3 = new KeyFigureMetaData();

		group.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {

			if(nv==null)
				return;

			if(nv.contains("All")) {
				initKeyFigureSelection(cseries1, type1, meta.getKeyFigures());
				initKeyFigureSelection(cseries2, type2, meta.getKeyFigures());
				initKeyFigureSelection(cseries3, type3, meta.getKeyFigures());
			} else if(nv.contains("used")) {
				initKeyFigureSelection(cseries1, type1, recent);
				initKeyFigureSelection(cseries2, type2, recent);
				initKeyFigureSelection(cseries3, type3, recent);
			}
			else {
				initKeyFigureSelection(cseries1, type1, meta.getGroupMap().get(nv));
				initKeyFigureSelection(cseries2, type2, meta.getGroupMap().get(nv));
				initKeyFigureSelection(cseries3, type3, meta.getGroupMap().get(nv));
			}
		});

		cseries1.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {

			if(nv!=null && ov != nv) {
				if(nv.hash!=0) {
					addToRecent(nv);
					series1.setName(nv.desc1+" ["+nv.uom+"]   ");
				}
				else
					series1.setName(nv.desc1+"   ");
				type1 = nv;
				prefs.putInt(MAVPreferences.LINECHART_FIG_1+id,nv.hash);
				updateRequest();
			}
		});

		cseries2.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {
			if(nv!=null && ov != nv) {
				if(nv.hash!=0) {
					addToRecent(nv);
					series2.setName(nv.desc1+" ["+nv.uom+"]   ");
				}
				else
					series2.setName(nv.desc1+"   ");
				type2 = nv;
				prefs.putInt(MAVPreferences.LINECHART_FIG_2+id,nv.hash);
				updateRequest();
			}
		});

		cseries3.getSelectionModel().selectedItemProperty().addListener((observable, ov, nv) -> {
			if(nv!=null && ov != nv) {
				if(nv.hash!=0) {
					addToRecent(nv);
					series3.setName(nv.desc1+" ["+nv.uom+"]   ");
				}
				else
					series3.setName(nv.desc1+"   ");
				type3 = nv;
				prefs.putInt(MAVPreferences.LINECHART_FIG_3+id,nv.hash);
				updateRequest();
			}
		});

		export.setOnAction((ActionEvent event)-> {
			saveAsPng(System.getProperty("user.home"));
			event.consume();
		});

		timeFrame.addListener((v, ov, nv) -> {

			current_x0_pt = dataService.calculateX0Index(dataService.getModelList().size()-1);
			this.current_x_pt = 0;
			setXResolution(timeFrame.get());
			xAxis.setTickUnit(resolution_ms/20);
			xAxis.setMinorTickCount(10);
		});


		scroll.addListener((v, ov, nv) -> {
			current_x0_pt =  dataService.calculateX0IndexByFactor(nv.floatValue());
			if(!isDisabled() && !refreshRequest) {
				refreshRequest = true;
				Platform.runLater(() -> {
					updateGraph(refreshRequest,0);
				});
			}

			if(!dataService.isCollecting() && !dataService.isReplaying() && !state.getConnectedProperty().get()) {
				dataService.setCurrent(dataService.calculateX1IndexByFactor(nv.floatValue())-1);
			}
		});

		replay.addListener((v, ov, nv) -> {
			Platform.runLater(() -> {
				if(nv.intValue()<=1) {
					updateGraph(true,1);
				} else
					updateGraph(false,nv.intValue());
			});
		});

		isScrolling.addListener((v, ov, nv) -> {
			if(nv.booleanValue())
				resolution_ms = resolution_ms * 2 ;
			else
				setXResolution(timeFrame.get());
		});

		dash.selectedProperty().addListener((v, ov, nv) -> {
			updateRequest();
		});

		annotations.setSelected(false);

		bckgmode.getSelectionModel().selectedIndexProperty().addListener((observable, ov, nv) -> {
			mode.setModeType(nv.intValue());
		});
	}


	public void setKeyFigureSelection(KeyFigurePreset preset) {

		Platform.runLater(() -> {
			if(preset!=null) {
				type1 = setKeyFigure(cseries1,preset.getKeyFigure(0));
				type2 = setKeyFigure(cseries2,preset.getKeyFigure(1));
				type3 = setKeyFigure(cseries3,preset.getKeyFigure(2));
				group.getSelectionModel().select(preset.getGroup());
				replay.set(0); updateRequest();
			}
		});
	}

	public KeyFigurePreset getKeyFigureSelection() {
		final KeyFigurePreset preset = new KeyFigurePreset(id,group.getSelectionModel().getSelectedIndex(),
				type1.hash,type2.hash,type3.hash);
		return preset;
	}

	public void returnToOriginalTimeScale() {
		if(dataService.isCollecting()) {
			if(isPaused) {
				Platform.runLater(() -> {
					current_x0_pt =  dataService.calculateX0IndexByFactor(scroll.get());
					setXResolution(timeFrame.get());
					updateGraph(true,0);
				});
				isRunning = true;
			}
			else {
				isRunning = false;
			}
			isPaused = !isPaused;
		}
		else {
			current_x0_pt =  dataService.calculateX0IndexByFactor(scroll.get());
			setXResolution(timeFrame.get());
			updateRequest();
		}
	}

	public void setZoom(double x0, double x1) {
		if((x1-x0)>1 && ( type1.hash!=0 || type2.hash!=0 || type3.hash!=0)) {
			current_x0_pt = (int)(x0 * 1000f / dataService.getCollectorInterval_ms());
			setXResolution((int)(x1-x0));
		}
		updateRequest();
	}

	public void registerSyncChart(IChartSyncControl syncChart) {
		this.syncCharts.add(syncChart);
	}

	public LineChartWidget setup(IMAVController control, int id) {

		this.id      = id;

		setXResolution(20);

		switch(id) {
		case 1:
			bckgmode.getSelectionModel().select(2);
			break;
		case 2:
			bckgmode.getSelectionModel().select(1);
			break;
		}

		series1.setName(type1.desc1);
		series2.setName(type2.desc1);
		series3.setName(type3.desc1);

		state.getRecordingProperty().addListener((o,ov,nv) -> {
			if(nv.intValue()!=AnalysisModelService.STOPPED ) {
				current_x0_pt = 0;
				scroll.setValue(0);
				isRunning = true;
			} else
				isRunning = false;
			setXResolution(timeFrame.get());
		});

		KeyFigureMetaData k1 = meta.getKeyFigureMap().get(prefs.getInt(MAVPreferences.LINECHART_FIG_1+id,0));
		if(k1!=null) type1 = k1;
		KeyFigureMetaData k2 = meta.getKeyFigureMap().get(prefs.getInt(MAVPreferences.LINECHART_FIG_2+id,0));
		if(k2!=null) type2 = k2;
		KeyFigureMetaData k3 = meta.getKeyFigureMap().get(prefs.getInt(MAVPreferences.LINECHART_FIG_3+id,0));
		if(k3!=null) type3 = k3;

		meta.addObserver((o,e) -> {

			group.getItems().clear();

			if(e == null) {

				group.getItems().add("Last used...");
				group.getItems().add("All");
				group.getItems().addAll(meta.getGroups());
				group.getSelectionModel().select(0);

				initKeyFigureSelection(cseries1, type1, recent);
				initKeyFigureSelection(cseries2, type2, recent);
				initKeyFigureSelection(cseries3, type3, recent);

			} else {

				group.getItems().add("All");
				group.getItems().addAll(meta.getGroups());
				group.getSelectionModel().select(0);

				initKeyFigureSelection(cseries1, type1, meta.getKeyFigures());
				initKeyFigureSelection(cseries2, type2, meta.getKeyFigures());
				initKeyFigureSelection(cseries3, type3, meta.getKeyFigures());

			}
		});

		this.getParent().disabledProperty().addListener((l,o,n) -> {
			if(!n.booleanValue()) {
				if(!state.getReplayingProperty().get()) {
					current_x0_pt =  dataService.calculateX0IndexByFactor(scroll.get());
					updateRequest();
				} else {
					updateGraph(true,replay.intValue());
				}
			}
		});

		return this;
	}

	public IntegerProperty getTimeFrameProperty() {
		return timeFrame;
	}

	@Override
	public FloatProperty getScrollProperty() {
		return scroll;
	}

	@Override
	public FloatProperty getReplayProperty() {
		return replay;
	}

	public BooleanProperty getIsScrollingProperty() {
		return isScrolling;
	}

	@Override
	public void refreshChart() {
		//	current_x0_pt = dataService.calculateX0IndexByFactor(1);
		setXResolution(timeFrame.get());
		if(!isDisabled())
			updateRequest();
	}

	public void saveAsPng(String path) {
		final SnapshotParameters param = new SnapshotParameters();
		param.setFill(Color.BLACK);
		WritableImage image = linechart.snapshot(param, null);
		File file = new File(path+"/chart_"+id+".png");
		try {
			ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
		} catch (IOException e) {  }
	}

	private void addToRecent(KeyFigureMetaData nv) {

		if(recent.size()>MAXRECENT)
			recent.remove(0);

		for(KeyFigureMetaData s : recent) {
			if(s.hash == nv.hash)
				return;
		}
		recent.add(nv);
		storeRecentList();
	}

	private void setXResolution(float frame) {

		if(frame >= 200)
			resolution_ms = 200;
		else if(frame >= 60)
			resolution_ms = 100;
		else if(frame >= 30)
			resolution_ms = 50;
		else if(frame >= 15)
			resolution_ms = 50;
		else
			resolution_ms = dataService.getCollectorInterval_ms();

		final int factor = dataService.isCollecting()  ? 2 : 1;
		resolution_ms = resolution_ms * factor;

		timeframe = frame;

		if(!isDisabled()) {
			updateRequest();
			Platform.runLater(() -> {
				xAxis.setLabel("Seconds ("+resolution_ms+"ms)");
			});
		}
	}

	private void updateRequest() {
		if(!isDisabled() && !refreshRequest) {
			refreshRequest = true;

			Platform.runLater(() -> {
				if(!state.getReplayingProperty().get()) {
					//	current_x0_pt = dataService.calculateX0Index(dataService.getModelList().size()-1);
					updateGraph(refreshRequest,0);
				} else {
					updateGraph(refreshRequest,replay.intValue());
				}
			});
		}
	}

	private  void updateGraph(boolean refresh, int max_x0) {
		float dt_sec = 0; AnalysisDataModel m =null; boolean set_bounds = false; double v1 ; double v2; double v3;
		int max_x = 0; int size = dataService.getModelList().size(); long slot_tms = 0;

		if(isDisabled()) {
			return;
		}

		if(refresh) {

			if(size==0 && dataService.isCollecting()) {
				refreshRequest = true; return;
			}


			linechart.getAnnotations().clearAnnotations(Layer.FOREGROUND);
			last_annotation_pos = 0;
			yoffset = 0;
			dashboard_update_tms = 0;

			current_x_pt  = current_x0_pt;
			current_x1_pt = current_x0_pt + (int)(timeframe * 1000f / dataService.getCollectorInterval_ms());
			setXAxisBounds(current_x0_pt,current_x1_pt);

			mode.clear();

			if(size==0) {
				series1.getData().remove(0,series1.getData().size()-1);
				series2.getData().remove(0,series2.getData().size()-1);
				series3.getData().remove(0,series3.getData().size()-1);
			} else {
				series1.getData().clear();
				series2.getData().clear();
				series3.getData().clear();
			}

			//		pool.invalidateAll();


			if(dash.isSelected() && size> 0) {

				if(type1.hash!=0)
					linechart.getAnnotations().add(dashboard1, Layer.FOREGROUND);
				if(type2.hash!=0)
					linechart.getAnnotations().add(dashboard2, Layer.FOREGROUND);
				if(type3.hash!=0)
					linechart.getAnnotations().add(dashboard3, Layer.FOREGROUND);

			}

			// Workaround to force chart refresh

			linechart.getData().clear();
			linechart.getData().add(series1);
			linechart.getData().add(series2);
			linechart.getData().add(series3);

			yAxis.setUpperBound(1); yAxis.setLowerBound(0);

		}


		if(current_x_pt<size && size>0 ) {

			if(state.getRecordingProperty().get()==AnalysisModelService.STOPPED  || isPaused) {
				if(max_x0 > 0) {
					max_x = max_x0;
				}
				else
					max_x = current_x1_pt < size ?  current_x1_pt : size ;
			} else {
				max_x = size;
			}


			if(dash.isSelected() && size>0 	&& ( (System.currentTimeMillis()-dashboard_update_tms) > 500 )|| refresh ) {
				dashboard_update_tms = System.currentTimeMillis();
				setDashboardData(dashboard1,type1, current_x0_pt,current_x1_pt);
				setDashboardData(dashboard2,type2, current_x0_pt,current_x1_pt);
				setDashboardData(dashboard3,type3, current_x0_pt,current_x1_pt);
			}

			slot_tms = System.currentTimeMillis();

			while(current_x_pt<max_x && size>0 && current_x_pt< dataService.getModelList().size() &&
					((System.currentTimeMillis()-slot_tms) < REFRESH_SLOT || refreshRequest)) {


				m = dataService.getModelList().get(current_x_pt);
				dt_sec = current_x_pt *  dataService.getCollectorInterval_ms() / 1000f;

				if(m.msg!=null && current_x_pt > 0 && m.msg!=null && m.msg.text!=null
						&& ( type1.hash!=0 || type2.hash!=0 || type3.hash!=0)
						&& display_annotations) {

					if((current_x_pt - last_annotation_pos) > 150 || yoffset > 12)
						yoffset=0;

					linechart.getAnnotations().add(new LineMessageAnnotation(this,dt_sec,yoffset++, m.msg,
							(resolution_ms<300) && annotations.isSelected()),
							Layer.FOREGROUND);
					last_annotation_pos = current_x_pt;
				}

				if(((current_x_pt * dataService.getCollectorInterval_ms()) % resolution_ms) == 0 && current_x_pt > 0) {
					if(current_x_pt > current_x1_pt) {
						if(series1.getData().size()>0 && type1.hash!=0) {
							pool.invalidate(series1.getData().get(0));
							series1.getData().remove(0);
						}

						if(series2.getData().size()>0 && type2.hash!=0) {
							pool.invalidate(series2.getData().get(0));
							series2.getData().remove(0);
						}

						if(series3.getData().size()>0 && type3.hash!=0) {
							pool.invalidate(series3.getData().get(0));
							series3.getData().remove(0);
						}
					}

					if( type1.hash!=0 || type2.hash!=0 || type3.hash!=0) {
						mode.updateModeData(dt_sec, m);
					}

					if(type1.hash!=0)  {
						v1 = determineValueFromRange(current_x_pt,resolution_ms/dataService.getCollectorInterval_ms(),type1,false);
						if(!Double.isNaN(v1))
							series1.getData().add(pool.checkOut(dt_sec,v1));

					}
					if(type2.hash!=0)  {
						v2 = determineValueFromRange(current_x_pt,resolution_ms/dataService.getCollectorInterval_ms(),type2,false);
						if(!Double.isNaN(v2))
							series2.getData().add(pool.checkOut(dt_sec,v2));
					}
					if(type3.hash!=0)  {
						v3 = determineValueFromRange(current_x_pt,resolution_ms/dataService.getCollectorInterval_ms(),type3,false);
						if(!Double.isNaN(v3))
							series3.getData().add(pool.checkOut(dt_sec,v3));
					}
				}


				if(current_x_pt > current_x1_pt) {
					set_bounds = true;
					if(!isPaused) {
						current_x0_pt += refresh_step;
						current_x1_pt += refresh_step;
					}
				}
				current_x_pt++;
			}

			if(set_bounds) {
				setXAxisBounds(current_x0_pt,current_x1_pt);
				set_bounds=false;
			}
		}
		refreshRequest = false;
	}

	private void setDashboardData(DashBoardAnnotation d, KeyFigureMetaData kf, int x0, int x1) {
		int count=0; double val=0;
		double _min = Double.NaN; double _max = Double.NaN;
		double _avg = 0; double mean = 0; double std=0;

		if(kf== null || kf.hash==0)
			return;
		d.setKeyFigure(kf);

		for(int i =x0; i < x1 && i< dataService.getModelList().size();i++) {
			val = dataService.getModelList().get(i).getValue(kf);
			if(!Double.isNaN(val) && !Double.isInfinite(val)) {
				if(val<_min || Double.isNaN(_min)) _min = val;
				if(val>_max || Double.isNaN(_max)) _max = val;
				_avg = _avg + val; count++;
			}
		}

		d.setMinMax(_min, _max);
		if(count>0) {
			mean = _avg / count; std = 0;
			for(int i = x0; i < x1 && i< dataService.getModelList().size();i++) {
				val = dataService.getModelList().get(i).getValue(kf);
				std = std + (val - mean) * (val - mean);
			}
			std = (float)Math.sqrt(std / count);
			d.setAvg(mean, std);
		}
	}


	private  void setXAxisBounds(int lower_pt, int upper_pt) {
		double tick = timeframe/6;
		if(tick < 1) tick = 1;
		xAxis.setTickUnit(tick);
		double lb = lower_pt * dataService.getCollectorInterval_ms() / 1000F;
		double hb = upper_pt * dataService.getCollectorInterval_ms() / 1000f;
		mode.setBounds(lb, hb);
		xAxis.setLowerBound(lb);
		xAxis.setUpperBound(hb);
	}


	private KeyFigureMetaData setKeyFigure(ChoiceBox<KeyFigureMetaData> series,int keyFigureHash) {
		final KeyFigureMetaData v = meta.getKeyFigureMap().get(keyFigureHash);
		if(v!=null) {
			if(!series.getItems().contains(v))
				series.getItems().add(v);
			series.getSelectionModel().select(v);
			return v;
		} else {
			series.getSelectionModel().select(0);
		}
		return new KeyFigureMetaData();
	}

	private void initKeyFigureSelection(ChoiceBox<KeyFigureMetaData> series,KeyFigureMetaData type, List<KeyFigureMetaData> kfl) {

		final KeyFigureMetaData none = new KeyFigureMetaData();

		Platform.runLater(() -> {

			series.getItems().clear();
			series.getItems().add(none);

			if(kfl.size()==0) {
				series.getSelectionModel().select(0);
				return;
			}

			if(type!=null && type.hash!=0) {
				if(!kfl.contains(type))
					series.getItems().add(type);
				series.getItems().addAll(kfl);
				series.getSelectionModel().select(type);
			} else {
				series.getItems().addAll(kfl);
				series.getSelectionModel().select(0);
			}

		});
	}

	private void storeRecentList() {
		try {
			final String rc = gson.toJson(recent);
			prefs.put(MAVPreferences.RECENT_FIGS, rc);
		} catch(Exception w) { }
	}

	private void readRecentList() {

		final String rc = prefs.get(MAVPreferences.RECENT_FIGS, null);
		try {
			if(rc!=null)
				recent = gson.fromJson(rc, new TypeToken<ArrayList<KeyFigureMetaData>>() {}.getType());
		} catch(Exception w) { }
		if(recent==null)
			recent = new ArrayList<KeyFigureMetaData>();

	}

	/*
	 * Determines spikes or average, if not all datapoints are reported.
	 */
	private double determineValueFromRange(int current_x, int length, KeyFigureMetaData m, boolean average) {

		try {

			final double v_current_x = dataService.getModelList().get(current_x).getValue(m);


			if(dataService.getModelList().size() < length || Double.isNaN(v_current_x))
				return 0;

			if(length==1)
				return v_current_x;

			double a = 0; double v;

			if(average) {
				a = v_current_x;
				for(int i=current_x-length+1;i<current_x;i++)
					a = a + dataService.getModelList().get(i).getValue(m);
				return a / length;

			} else {

				int peak_index=current_x;
				double max = Math.abs(v_current_x);

				for(int i=current_x-length+1;i<current_x;i++) {
					v = Math.abs(dataService.getModelList().get(i).getValue(m));
					if(v>max && v != Float.NaN)
						max = v; peak_index = i;
				}
				return dataService.getModelList().get(peak_index).getValue(m);
			}

		} catch(IndexOutOfBoundsException o) {
			return 0;
		}
	}

}
