/**
 * Copyright (C) 2009 Michael A. MacDonald
 */
package com.kenfenheuer.vncclient;

import android.widget.ImageView.ScaleType;

/**
 * @author Michael A. MacDonald
 */
class FitToScreenScaling extends AbstractScaling {

	/**
	 * @param id
	 * @param scaleType
	 */
	FitToScreenScaling() {
		super(1, ScaleType.FIT_CENTER);
	}

	@Override
	float getScale() {
		return super.getScale();
	}

	/* (non-Javadoc)
         * @see android.androidVNC.AbstractScaling#isAbleToPan()
         */
	@Override
	boolean isAbleToPan() {
		return false;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#isValidInputMode(int)
	 */
	@Override
	boolean isValidInputMode(int mode) {
		return mode == R.id.itemInputFitToScreen;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#getDefaultHandlerId()
	 */
	@Override
	int getDefaultHandlerId() {
		return R.id.itemInputFitToScreen;
	}

	/* (non-Javadoc)
	 * @see android.androidVNC.AbstractScaling#setCanvasScaleType(android.androidVNC.VncCanvas)
	 */
	@Override
	void setScaleTypeForActivity(VncCanvasActivity activity) {
		super.setScaleTypeForActivity(activity);
		activity.vncCanvas.absoluteXPosition = activity.vncCanvas.absoluteYPosition = 0;
		activity.vncCanvas.scrollTo(0, 0);
	}

}
