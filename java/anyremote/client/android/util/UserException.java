//
// anyRemote android client
// a bluetooth/wi-fi remote control for Linux.
//
// Copyright (C) 2011-2016 Mikhail Fedotov <anyremote@mail.ru>
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
//

package anyremote.client.android.util;

public class UserException extends Exception {
	private String error, details;
	
	static final long serialVersionUID = 1232123;

	public UserException(String error, String details) {

		this(error, details, null);
	}

	public UserException(String error, String details, Exception ex) {

		this.error = error;

		if (details == null) {
			if (ex != null && ex.getMessage() != null) {
				this.details = ex.getMessage();
			} else {
				this.details = "";
			}
		} else {
			if (ex != null && ex.getMessage() != null) {
				if (details.endsWith(".")) {
					details = details.substring(0, details.length() - 1);
				}
				this.details = details + " (" + ex.getMessage() + ").";
			} else {
				this.details = details;
			}
		}

	}

	// Get the error details
	public String getDetails() {
		return details;
	}

	// Get the error title
	public String getError() {
		return error;
	}
}
