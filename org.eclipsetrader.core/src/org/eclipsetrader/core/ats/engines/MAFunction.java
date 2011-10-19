/*
 * Copyright (c) 2004-2011 Marco Maccaferri and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marco Maccaferri - initial API and implementation
 */

package org.eclipsetrader.core.ats.engines;

import java.util.List;

import org.eclipsetrader.core.feed.IBar;
import org.mozilla.javascript.Scriptable;

import com.tictactec.ta.lib.MInteger;

public class MAFunction extends BaseDataSeriesFunction {

    private static final long serialVersionUID = 486926041698543859L;

    private int field = Util.FIELD_CLOSE;
    private int period;
    private int type;

    public MAFunction() {
    }

    public MAFunction(List<IBar> bars, int type, int period) {
        super(bars);
        this.type = type;
        this.period = period;
        updateData();
    }

    public Object jsFunction_crosses(MAFunction other, double value) throws Exception {
        updateData();
        other.updateData();

        Double ourLast = getLast();
        Double otherLast = other.getLast();
        if (ourLast == null || otherLast == null) {
            return 0;
        }

        Double ourNext = calculateNextValue(value);
        Double otherNext = other.calculateNextValue(value);

        if (ourLast < otherLast && ourNext > otherNext) {
            return 1;
        }
        if (ourLast > otherLast && ourNext < otherNext) {
            return -1;
        }

        return 0;
    }

    public Object jsFunction_crosses(MAFunction other, IBar bar) throws Exception {
        switch (field) {
            case Util.FIELD_OPEN:
                return jsFunction_crosses(other, bar.getOpen());

            case Util.FIELD_HIGH:
                return jsFunction_crosses(other, bar.getHigh());

            case Util.FIELD_LOW:
                return jsFunction_crosses(other, bar.getLow());

            case Util.FIELD_CLOSE:
                return jsFunction_crosses(other, bar.getClose());

            case Util.FIELD_VOLUME:
                return jsFunction_crosses(other, bar.getVolume());
        }
        return 0;
    }

    protected Double calculateNextValue(double value) {
        List<IBar> bars = getBars();
        if (bars == null) {
            return null;
        }

        int lookback = core.movingAverageLookback(period, Util.getTALib_MAType(type));
        if (bars.size() < lookback) {
            return null;
        }

        double[] inReal = new double[bars.size() + 1];
        Util.copyValuesTo(bars, field, inReal);
        inReal[bars.size()] = value;

        double[] outReal = new double[inReal.length - lookback];

        int startIdx = 0;
        int endIdx = inReal.length - 1;
        MInteger outBegIdx = new MInteger();
        MInteger outNbElement = new MInteger();

        core.movingAverage(startIdx, endIdx, inReal, period, Util.getTALib_MAType(type), outBegIdx, outNbElement, outReal);

        return outReal[outReal.length - 1];
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.ats.engines.BaseDataSeriesFunction#get(java.lang.String, org.mozilla.javascript.Scriptable)
     */
    @Override
    public Object get(String name, Scriptable start) {
        updateData();
        return super.get(name, start);
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.ats.engines.BaseDataSeriesFunction#get(int, org.mozilla.javascript.Scriptable)
     */
    @Override
    public Object get(int index, Scriptable start) {
        updateData();
        return super.get(index, start);
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.ats.engines.BaseDataSeriesFunction#getIds()
     */
    @Override
    public Object[] getIds() {
        updateData();
        return super.getIds();
    }

    /* (non-Javadoc)
     * @see org.eclipsetrader.core.ats.engines.BaseDataSeriesFunction#has(int, org.mozilla.javascript.Scriptable)
     */
    @Override
    public boolean has(int index, Scriptable start) {
        updateData();
        return super.has(index, start);
    }

    /* (non-Javadoc)
     * @see org.mozilla.javascript.ScriptableObject#getClassName()
     */
    @Override
    public String getClassName() {
        return "MA";
    }

    private void updateData() {
        List<IBar> bars = getBars();
        if (bars == null) {
            return;
        }

        int lookback = core.movingAverageLookback(period, Util.getTALib_MAType(type));
        if (bars.size() < lookback) {
            return;
        }

        double[] existingData = getData();
        if (existingData != null && existingData.length == (bars.size() - lookback)) {
            return;
        }

        int startIdx = 0;
        int endIdx = bars.size() - 1;
        double[] inReal = new double[bars.size()];
        Util.copyValuesTo(bars, field, inReal);

        MInteger outBegIdx = new MInteger();
        MInteger outNbElement = new MInteger();
        double[] outReal = new double[inReal.length - lookback];

        core.movingAverage(startIdx, endIdx, inReal, period, Util.getTALib_MAType(type), outBegIdx, outNbElement, outReal);

        setData(outReal);
    }
}