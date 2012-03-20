//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011 Mikhail Fedotov <anyremote@mail.ru>
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
//

package anyremote.client.android;

import java.util.Timer;
import java.util.TimerTask;

public class MainLoop {

	private static Timer timer;

	//Disable the main loop. Does nothing if the loop is already disabled
	public static void disable() {

		if (timer != null) {
			timer.cancel();
			timer = null;
		}

	}

	// Enable the main loop. Does nothing if the loop is already enabled
	public static void enable() {

		if (timer == null) {
			timer = new Timer();
		}
	}

	/** See {@link Timer#schedule(TimerTask, long)} with 100ms delay. */
	public static void schedule(TimerTask task) {
		if (timer == null) return;
		timer.schedule(task, 100);
	}

	/** See {@link Timer#schedule(TimerTask, long)}. */
/*	private static void schedule(TimerTask task, long delay) {
		if (timer == null) return;
		timer.schedule(task, delay);
	}*/

	/** See {@link Timer#schedule(TimerTask, long, long)}. */
	//public static void schedule(TimerTask task, long delay, long period) {
	//	if (timer == null) return;
	//	timer.schedule(task, delay, period);
	//}
}
