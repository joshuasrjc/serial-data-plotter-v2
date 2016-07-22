import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

import javax.swing.*;

import com.fazecast.jSerialComm.*;

public class SerialDataPlotterV2 extends JPanel implements Runnable, ActionListener
{
	public static final Color[] CHANNEL_COLORS = {
		Color.RED,
		Color.BLUE,
		new Color(0, 191, 0), // Green
		new Color(255, 127, 0),
		Color.CYAN,
		Color.MAGENTA,
		Color.BLACK
	};
	public static final Color DEFAULT_COLOR = Color.GRAY;
	public static final Font LIST_FONT = new Font("Courier New", Font.PLAIN, 24);
	public static final Font CHECK_BOX_FONT = new Font("Courier New", Font.PLAIN, 12);
	public static final String CONNECT = "Connect";
	public static final String DISCONNECT = "Disconnect";
	
	public static void main(String[] args)
	{
		new SerialDataPlotterV2();
	}
	
	private Thread thread;
	private JFrame frame;
	private BufferedReader in;
	
	private JPanel commsPanel = new JPanel();
	DefaultListModel<String> listModel;
	private JList<String> list;
	private JButton connect;
	private JButton refresh;
	private String connectedPortName = null;
	private boolean refreshing = false;
	
	private JPanel channelsPanel = new JPanel();
	private ArrayList<JCheckBox> checkBoxes = new ArrayList<JCheckBox>();
	
	private ArrayList<ArrayList<DataPoint>> channels = new ArrayList<ArrayList<DataPoint>>();
	private int time = 0;
	
	private boolean logging = false;
	private boolean painting = false;
	
	SerialDataPlotterV2()
	{
		frame = new JFrame("Serial Data Plotter V2");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.LINE_AXIS));
		
		commsPanel = new JPanel();
		commsPanel.setLayout(new BorderLayout());
		commsPanel.setPreferredSize(new Dimension(200, 0));
		commsPanel.setMaximumSize(new Dimension(200, 10000));
		
		listModel = new DefaultListModel<String>();
		
		refresh = new JButton("Refresh");
		refresh.addActionListener(this);
		commsPanel.add(refresh, BorderLayout.NORTH);
		list = new JList<String>(listModel);
		list.setFont(LIST_FONT);
		commsPanel.add(list, BorderLayout.CENTER);
		connect = new JButton(CONNECT);
		connect.addActionListener(this);
		commsPanel.add(connect, BorderLayout.SOUTH);
		
		frame.add(commsPanel);
		frame.add(Box.createRigidArea(new Dimension(5, 0)));
		
		channelsPanel = new JPanel();
		channelsPanel.setPreferredSize(new Dimension(128,0));
		channelsPanel.setMaximumSize(new Dimension(128,10000));
		frame.add(channelsPanel);
		frame.add(Box.createRigidArea(new Dimension(5, 0)));
		
		this.setPreferredSize(new Dimension(800, 600));
		frame.add(this);
		
		frame.pack();
		frame.setVisible(true);
		
		thread = new Thread(this);
		thread.start();
	}
	
	private void logData(String str)
	{
		String[] strs = str.split(",");
		float[] data = new float[strs.length];
		for(int i = 0; i < data.length; i++)
		{
			data[i] = Float.NaN;
			try
			{
				data[i] = Float.parseFloat(strs[i]);
			}
			catch(Exception ex)
			{
				System.err.println("Error parsing float: " + strs[i]);
			}
		}
		
		logData(data);
	}
	
	private void logData(float[] data)
	{
		while(painting)
		{
			try{Thread.sleep(1);}catch(Exception ex){}
		}
		logging = true;
		for(int i = 0; i < data.length; i++)
		{
			ArrayList<DataPoint> channel;
			if(i >= channels.size())
			{
				channel = new ArrayList<DataPoint>();
				channels.add(channel);
				createCheckBox();
			}
			else
			{
				channel = channels.get(i);
			}
			channel.add(new DataPoint(time, data[i]));
		}
		
		time++;
		repaint();
		logging = false;
	}
	
	private Color getColor(int i)
	{
		if(i < 0 || i >= CHANNEL_COLORS.length)
		{
			return DEFAULT_COLOR;
		}
		return CHANNEL_COLORS[i];
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		while(logging)
		{
			try{Thread.sleep(1);}catch(Exception ex){}
		}
		painting = true;
		
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, this.getWidth(), this.getHeight());
		
		float minValue = Float.MAX_VALUE;
		float maxValue = Float.MIN_VALUE;
		
		for(int i = 0; i < channels.size(); i++)
		{
			if(!checkBoxIsTicked(i)) continue;
			ArrayList<DataPoint> channel = channels.get(i);
			for(DataPoint dp : channel)
			{
				float value = dp.v;
				if(value < minValue)
				{
					minValue = value;
				}
				if(value > maxValue)
				{
					maxValue = value;
				}
			}
		}
		
		Graphics2D g2d = (Graphics2D)g;
		float midValue = (minValue + maxValue) / 2;
		float scaling = getHeight() / (maxValue - minValue);
		g2d.setColor(Color.BLACK);
		g2d.setStroke(new BasicStroke(0.0001f));
		Shape axis = new Line2D.Float(0, scaling * midValue, this.getWidth(), scaling * midValue);
		g2d.translate(0, getHeight() / 2);
		g2d.draw(axis);

		g2d.setColor(Color.BLACK);
		int magnitude = (int) Math.log10(maxValue - minValue);
		double power = Math.pow(10, magnitude - 1);
		for(int i = -10; i <= 10; i++)
		{
			double y1 = scaling * (midValue - power * i);
			Shape tick1 = new Line2D.Double(32, y1, 48, y1);
			//g2d.drawString((10*power*i) + "", 0, (float)y1);
			
			double y2 = scaling * (midValue - 10 * power * i);
			Shape tick2 = new Line2D.Double(32, y2, 64, y2);
			g2d.drawString((10*power*i) + "", 0, (float)y2);

			g2d.draw(tick1);
			g2d.draw(tick2);
		}
		
		g2d.setStroke(new BasicStroke(5f));
		for(int i = 0; i < channels.size(); i++)
		{
			if(!checkBoxIsTicked(i)) continue;
			ArrayList<DataPoint> channel = channels.get(i);
			g2d.setColor(getColor(i));
			DataPoint lastDP = null;
			for(DataPoint dp : channel)
			{
				if(lastDP != null)
				{
					float x1 = getWidth() * lastDP.t / time - 1;
					float y1 = scaling * (midValue - lastDP.v) - 1;
					float x2 = getWidth() * dp.t / time - 1;
					float y2 = scaling * (midValue - dp.v) - 1;
					Shape line = new Line2D.Float(x1, y1, x2, y2);
					g2d.draw(line);
				}
				lastDP = dp;
			}
		}
		
		painting = false;
	}
	
	private boolean checkBoxIsTicked(int i)
	{
		if(i < 0 || i >= checkBoxes.size())
		{
			return false;
		}
		
		JCheckBox checkBox = checkBoxes.get(i);
		return checkBox.isSelected();
	}
	
	private void createCheckBox()
	{
		int i = checkBoxes.size();
		JCheckBox checkBox = new JCheckBox("Channel " + i);
		checkBox.setFont(CHECK_BOX_FONT);
		checkBox.setForeground(getColor(i));
		checkBox.setSelected(true);
		checkBoxes.add(checkBox);
		channelsPanel.add(checkBox);
		channelsPanel.revalidate();
		channelsPanel.repaint();
	}

	@Override
	public void run()
	{
		while(true)
		{
			listModel.clear();
			for(SerialPort port : SerialPort.getCommPorts())
			{
				listModel.addElement(port.getSystemPortName());
				frame.pack();
			}
			
			refreshing = false;
			
			Scanner scanner = null;
			SerialPort port = null;

			while(connectedPortName == null && !refreshing)
			{
				try{Thread.sleep(10);}catch(Exception ex){}
			}
			if(!refreshing)
			{
				try
				{
					
					port = SerialPort.getCommPort(connectedPortName);
					port.openPort();
					port.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
					scanner = new Scanner(port.getInputStream());
					
					while(connectedPortName != null)
					{
						String line = scanner.nextLine();
						logData(line);
						System.out.println(line);
					}
				}
				catch(Exception ex)
				{
					System.out.println("Port disconnected.");
				}
				finally
				{
					connectedPortName = null;
					connect.setText(CONNECT);
					try
					{
						System.out.println("Closing port...");
						scanner.close();
						port.closePort();
						System.out.println("Port closed.");
					}
					catch(Exception ex)
					{
						System.err.println("Error closing port.");
					}
				}
			}
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev)
	{
		if(ev.getSource() == connect)
		{
			if(connect.getText().equals(CONNECT))
			{
				connectedPortName = list.getSelectedValue();
				connect.setText(DISCONNECT);
			}
			else
			{
				connectedPortName = null;
				connect.setText(CONNECT);
			}
		}
		else if(ev.getSource() == refresh)
		{
			refreshing = true;
		}
	}
	
	private class DataPoint
	{
		int t; // Time
		float v; // Value
		
		DataPoint(int t, float v)
		{
			this.t = t;
			this.v = v;
		}
		
		@Override
		public String toString()
		{
			return String.format("%.2f", v);
		}
	}
}
