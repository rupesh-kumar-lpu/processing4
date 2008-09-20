package processing.candy;

import java.awt.Paint;
import java.util.HashMap;

import processing.core.*;
import processing.xml.XMLElement;


public class BaseObject extends PShape {
	XMLElement element;

	float opacity;

	Gradient strokeGradient;
	Paint strokeGradientPaint;
	String strokeName;  // id of another object, gradients only?

	Gradient fillGradient;
	Paint fillGradientPaint;
	String fillName;  // id of another object



	public BaseObject(BaseObject parent, XMLElement properties) {
		//super(GROUP);
		
		if (parent == null) {
			// set values to their defaults according to the SVG spec
			stroke = false;
			strokeColor = 0xff000000;
			strokeWeight = 1;
			strokeCap = PConstants.SQUARE;  // equivalent to BUTT in svg spec
			strokeJoin = PConstants.MITER;
			strokeGradient = null;
			strokeGradientPaint = null;
			strokeName = null;

			fill = true;
			fillColor = 0xff000000;
			fillGradient = null;
			fillGradientPaint = null;
			fillName = null;

			//hasTransform = false;
			//transformation = null; //new float[] { 1, 0, 0, 1, 0, 0 };

			opacity = 1;

		} else {
			stroke = parent.stroke;
			strokeColor = parent.strokeColor;
			strokeWeight = parent.strokeWeight;
			strokeCap = parent.strokeCap;
			strokeJoin = parent.strokeJoin;
			strokeGradient = parent.strokeGradient;
			strokeGradientPaint = parent.strokeGradientPaint;
			strokeName = parent.strokeName;

			fill = parent.fill;
			fillColor = parent.fillColor;
			fillGradient = parent.fillGradient;
			fillGradientPaint = parent.fillGradientPaint;
			fillName = parent.fillName;

			//hasTransform = parent.hasTransform;
			//transformation = parent.transformation;

			opacity = parent.opacity;
		}

		element = properties;

		name = properties.getStringAttribute("id");
		if (name != null) {
			table.put(name, this);
			//System.out.println("now parsing " + id);
		}

		String displayStr = properties.getStringAttribute("display", "inline");
		visible = !displayStr.equals("none");

		String transformStr = properties.getStringAttribute("transform");
		if (transformStr != null) {
			float[] t = parseMatrix(transformStr);
			matrix = new PMatrix3D(t[0], t[1], t[2], t[3], t[4], t[5]);
		}
		
		parseColors(properties);
	}



    // http://www.w3.org/TR/SVG/coords.html#TransformAttribute
	static protected float[] parseMatrix(String matrixStr) {
		/*
		String prefix = "matrix(";
		int start = matrixStr.indexOf(prefix);
		if (start == -1) return null;
		int stop = matrixStr.indexOf(')');
		String content = matrixStr.substring(start + matrixStr.length(), stop);
		return PApplet.parseFloat(PApplet.splitTokens(content.trim()));
		*/
		String[] pieces = PApplet.match(matrixStr, "\\s*(\\w+)\\((.*)\\)");
		if (pieces == null) {
			System.err.println("Could not parse transform " + matrixStr);
			return null;
		}
		float[] m = PApplet.parseFloat(PApplet.splitTokens(pieces[2]));
		
		if (pieces[1].equals("matrix")) {
			return m;
			
		} else if (pieces[1].equals("translate")) {
			float tx = m[0];
			float ty = (m.length == 2) ? m[1] : m[0];
			return new float[] { 1, 0, tx,  0, 1, ty };
			
		} else if (pieces[1].equals("scale")) {
			float sx = m[0];
			float sy = (m.length == 2) ? m[1] : m[0];
			return new float[] { sx, 0, 0, 0, sy, 0 };

		} else if (pieces[1].equals("rotate")) {
			float angle = m[0];
			
			if (m.length == 1) {
				float c = PApplet.cos(angle);
				float s = PApplet.sin(angle);
				return new float[] { c, -s, 0,  s, c, 0 };

			} else if (m.length == 3) {
				PMatrix2D mat = new PMatrix2D(0, 1, m[1],  1, 0, m[2]);
				mat.rotate(m[0]);
				mat.translate(-m[1], -m[2]);
				return mat.get(null);
			}
			
		} else if (pieces[1].equals("skewX")) {
			return new float[] { 1, PApplet.tan(m[0]), 0,  0, 1, 0 };
			
		} else if (pieces[1].equals("skewY")) {
			return new float[] { 1, 0, 0,  PApplet.tan(m[0]), 1, 0 };
		}
		return null;
	}
	
	
	/*
	protected void parseTransformation2(XMLElement properties) {
		String transform = 
		if (transform != null) {
			//this.hasTransform = true;
			transform = transform.substring(7, transform.length() - 2);
			String tf[] = PApplet.splitTokens(transform);
			float[] transformation = PApplet.parseFloat(tf);
			matrix = new PMatrix3D(transformation[0], transformation[1], transformation[2],
					transformation[3], transformation[4], transformation[5]);
		}
	}
	*/


	/*
    static protected AffineTransform parseTransform(String what) {
        if (what != null) {
            if (what.startsWith("matrix(") && what.endsWith(")")) {
                // columns go first with AT constructor
                what = what.substring(7, what.length() - 1);
                return new AffineTransform(PApplet.parseFloat(PApplet.split(what, ' ')));
            }
        }
        return null;
    }
    */

	
	protected void parseColors(XMLElement properties) {
		if (properties.hasAttribute("opacity")) {
			opacity = properties.getFloatAttribute("opacity");
		}
		int opacityMask = ((int) (opacity * 255)) << 24;

		if (properties.hasAttribute("stroke")) {
			String strokeText = properties.getStringAttribute("stroke");
			if (strokeText.equals("none")) {
				stroke = false;
			} else if (strokeText.startsWith("#")) {
				stroke = true;
				strokeColor = opacityMask |
				(Integer.parseInt(strokeText.substring(1), 16)) & 0xFFFFFF;
			} else if (strokeText.startsWith("rgb")) {
				stroke = true;
				strokeColor = opacityMask | parseRGB(strokeText);
			} else if (strokeText.startsWith("url(#")) {
				strokeName = strokeText.substring(5, strokeText.length() - 1);
				Object strokeObject = table.get(strokeName);
				if (strokeObject instanceof Gradient) {
					strokeGradient = (Gradient) strokeObject;
					strokeGradientPaint = calcGradientPaint(strokeGradient); //, opacity);
				} else {
					System.err.println("url " + strokeName + " refers to unexpected data");
				}
			}
		}

		if (properties.hasAttribute("stroke-width")) {
			// if NaN (i.e. if it's 'inherit') then default back to the inherit setting
			strokeWeight = properties.getFloatAttribute("stroke-width", strokeWeight);
		}

		if (properties.hasAttribute("stroke-linejoin")) {
			String linejoin = properties.getStringAttribute("stroke-linejoin");
			if (linejoin.equals("inherit")) {
				// do nothing, will inherit automatically

			} else if (linejoin.equals("miter")) {
				strokeJoin = PConstants.MITER;

			} else if (linejoin.equals("round")) {
				strokeJoin = PConstants.ROUND;

			} else if (linejoin.equals("bevel")) {
				strokeJoin = PConstants.BEVEL;
			}
		}

		if (properties.hasAttribute("stroke-linecap")) {
			String linecap = properties.getStringAttribute("stroke-linecap");
			if (linecap.equals("inherit")) {
				// do nothing, will inherit automatically

			} else if (linecap.equals("butt")) {
				strokeCap = PConstants.SQUARE;

			} else if (linecap.equals("round")) {
				strokeCap = PConstants.ROUND;

			} else if (linecap.equals("square")) {
				strokeCap = PConstants.PROJECT;
			}
		}

		// fill defaults to black (though stroke defaults to "none")
		// http://www.w3.org/TR/SVG/painting.html#FillProperties
		if (properties.hasAttribute("fill")) {
			String fillText = properties.getStringAttribute("fill");
			if (fillText.equals("none")) {
				fill = false;
			} else if (fillText.startsWith("#")) {
				fill = true;
				fillColor = opacityMask |
				(Integer.parseInt(fillText.substring(1), 16)) & 0xFFFFFF;
				//System.out.println("hex for fill is " + PApplet.hex(fillColor));
			} else if (fillText.startsWith("rgb")) {
				fill = true;
				fillColor = opacityMask | parseRGB(fillText);
			} else if (fillText.startsWith("url(#")) {
				fillName = fillText.substring(5, fillText.length() - 1);
				//PApplet.println("looking for " + fillName);
				Object fillObject = table.get(fillName);
				//PApplet.println("found " + fillObject);
				if (fillObject instanceof Gradient) {
					fill = true;
					fillGradient = (Gradient) fillObject;
					fillGradientPaint = calcGradientPaint(fillGradient); //, opacity);
					//PApplet.println("got filla " + fillObject);
				} else {
					System.err.println("url " + fillName + " refers to unexpected data");
				}
			}
		}
	}
	
	
	static protected int parseRGB(String what) {
		int leftParen = what.indexOf('(') + 1;
		int rightParen = what.indexOf(')');
		String sub = what.substring(leftParen, rightParen);
		int[] values = PApplet.parseInt(PApplet.splitTokens(sub, ", "));
		return (values[0] << 16) | (values[1] << 8) | (values[2]);
	}


    static protected HashMap<String, String> parseStyleAttributes(String style) {
    	HashMap<String, String> table = new HashMap<String, String>();
        String[] pieces = style.split(";");
        for (int i = 0; i < pieces.length; i++) {
            String[] parts = pieces[i].split(":");
            table.put(parts[0], parts[1]);
        }
        return table;
    }


    /**
     * Parse a size that may have a suffix for its units.
     * Ignoring cases where this could also be a percentage.
     * The <A HREF="http://www.w3.org/TR/SVG/coords.html#Units">units</A> spec:
     * <UL>
     * <LI>"1pt" equals "1.25px" (and therefore 1.25 user units)
     * <LI>"1pc" equals "15px" (and therefore 15 user units)
     * <LI>"1mm" would be "3.543307px" (3.543307 user units)
     * <LI>"1cm" equals "35.43307px" (and therefore 35.43307 user units)
     * <LI>"1in" equals "90px" (and therefore 90 user units)
     * </UL>
     */
    static protected float parseUnitSize(String text) {
        int len = text.length() - 2;

        if (text.endsWith("pt")) {
            return PApplet.parseFloat(text.substring(0, len)) * 1.25f;
        } else if (text.endsWith("pc")) {
            return PApplet.parseFloat(text.substring(0, len)) * 15;
        } else if (text.endsWith("mm")) {
            return PApplet.parseFloat(text.substring(0, len)) * 3.543307f;
        } else if (text.endsWith("cm")) {
            return PApplet.parseFloat(text.substring(0, len)) * 35.43307f;
        } else if (text.endsWith("in")) {
            return PApplet.parseFloat(text.substring(0, len)) * 90;
        } else if (text.endsWith("px")) {
            return PApplet.parseFloat(text.substring(0, len));
        } else {
            return PApplet.parseFloat(text);
        }
    }

    
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 


    // these are a set of hacks so that gradients can be hacked into Java 2D.
    
    /*
    protected Paint getGradient(String name, float cx, float cy, float r) {
        BaseObject obj = (BaseObject) findChild(name);

        if (obj != null) {
            if (obj.fillGradient != null) {
                return obj.calcGradientPaint(obj.fillGradient, cx, cy, r);
            }
        }
        throw new RuntimeException("No gradient found for shape " + name);
    }


    protected Paint getGradient(String name, float x1, float y1, float x2, float y2) {
        BaseObject obj = (BaseObject) findChild(name);

        if (obj != null) {
            if (obj.fillGradient != null) {
                return obj.calcGradientPaint(obj.fillGradient, x1, y1, x2, y2);
            }
        }
        throw new RuntimeException("No gradient found for shape " + name);
    }


    protected void strokeGradient(PGraphics g, String name, float x, float y, float r) {
        Paint paint = getGradient(name, x, y, r);

        if (g instanceof PGraphicsJava2D) {
            PGraphicsJava2D p2d = (PGraphicsJava2D) g;

            p2d.strokeGradient = true;
            p2d.strokeGradientObject = paint;
        }
    }
    

    protected void strokeGradient(PGraphics g, String name, float x1, float y1, float x2, float y2) {
        Paint paint = getGradient(name, x1, y1, x2, y2);

        if (g instanceof PGraphicsJava2D) {
            PGraphicsJava2D p2d = (PGraphicsJava2D) g;

            p2d.strokeGradient = true;
            p2d.strokeGradientObject = paint;
        }
    }


    protected void fillGradient(PGraphics g, String name, float x, float y, float r) {
        Paint paint = getGradient(name, x, y, r);

        if (g instanceof PGraphicsJava2D) {
            PGraphicsJava2D p2d = (PGraphicsJava2D) g;

            p2d.fillGradient = true;
            p2d.fillGradientObject = paint;
        }
    }


    protected void fillGradient(PGraphics g, String name, float x1, float y1, float x2, float y2) {
        Paint paint = getGradient(name, x1, y1, x2, y2);

        if (g instanceof PGraphicsJava2D) {
            PGraphicsJava2D p2d = (PGraphicsJava2D) g;

            p2d.fillGradient = true;
            p2d.fillGradientObject = paint;
        }
    }
    */


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 

    
	protected Paint calcGradientPaint(Gradient gradient) {
		if (gradient instanceof LinearGradient) {
			LinearGradient grad = (LinearGradient) gradient;
			return new LinearGradientPaint(grad.x1, grad.y1, grad.x2, grad.y2,
					grad.offset, grad.color, grad.count,
					opacity);

		} else if (gradient instanceof RadialGradient) {
			RadialGradient grad = (RadialGradient) gradient;
			return new RadialGradientPaint(grad.cx, grad.cy, grad.r,
					grad.offset, grad.color, grad.count,
					opacity);
		}
		return null;
	}


	protected Paint calcGradientPaint(Gradient gradient,
			float x1, float y1, float x2, float y2) {
		if (gradient instanceof LinearGradient) {
			LinearGradient grad = (LinearGradient) gradient;
			return new LinearGradientPaint(x1, y1, x2, y2,
					grad.offset, grad.color, grad.count,
					opacity);
		}
		throw new RuntimeException("Not a linear gradient.");
	}


	protected Paint calcGradientPaint(Gradient gradient,
			float cx, float cy, float r) {
		if (gradient instanceof RadialGradient) {
			RadialGradient grad = (RadialGradient) gradient;
			return new RadialGradientPaint(cx, cy, r,
					grad.offset, grad.color, grad.count,
					opacity);
		}
		throw new RuntimeException("Not a radial gradient.");
	}


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 

	
	protected void styles(PGraphics g) {
		super.styles(g);

		if (g instanceof PGraphicsJava2D) {
			PGraphicsJava2D p2d = (PGraphicsJava2D) g;

			if (strokeGradient != null) {
				p2d.strokeGradient = true;
				p2d.strokeGradientObject = strokeGradientPaint;
			} else {
				// need to shut off, in case parent object has a gradient applied
				//p2d.strokeGradient = false;
			}
			if (fillGradient != null) {
				p2d.fillGradient = true;
				p2d.fillGradientObject = fillGradientPaint;
			} else {
				// need to shut off, in case parent object has a gradient applied
				//p2d.fillGradient = false;
			}
		}
	}


    /**
     * Overrides SVG-set styles and uses PGraphics styles and colors.
     * Identical to ignoreStyles(true).
     */
    public void ignoreStyles() {
        //ignoreStyles(true);
    	styles = false;
    }


    /**
     * Enables or disables style information (fill and stroke) set in the file.
     * @param state true to use user-specified stroke/fill, false for svg version
     */
    public void ignoreStyles(boolean state) {
        //ignoreStyles = state;
    	styles = !state;
    }


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 
    
    
    public void drawImpl(PGraphics g) {
    	// do nothing
    }
    
    
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . 
    
    
	/**
	 * Get a particular element based on its SVG ID. When editing SVG by hand,
	 * this is the id="" tag on any SVG element. When editing from Illustrator,
	 * these IDs can be edited by expanding the layers palette. The names used
	 * in the layers palette, both for the layers or the shapes and groups
	 * beneath them can be used here.
	 * <PRE>
	 * // This code grabs "Layer 3" and the shapes beneath it.
	 * SVG layer3 = svg.get("Layer 3");
	 * </PRE>
	 */
	public PShape getChild(String name) {
		PShape found = super.getChild(name);
		if (found != null) return found;
		// otherwise try with underscores instead of spaces
		return super.getChild(name.replace(' ', '_'));
	}
	
	
	protected void parseGroup(XMLElement graphics) {
		XMLElement[] elements = graphics.getChildren();
		//objects = new BaseObject[elements.length];
		children = new PShape[elements.length];
		childCount = 0;

		for (XMLElement elem : elements) {
			addChild(parseGroupChild(elem));
		}

		//		for (int i = 0; i < elements.length; i++) {
		//			String name = elements[i].getName(); //getElement();
		//			XMLElement elem = elements[i];
		//			
		//			addChild(parseGroupChild(elem, name));
		//		}
	}


	protected PShape parseGroupChild(XMLElement elem) {
		String name = elem.getName();

		if (name.equals("g")) {
			return new BaseObject(this, elem);

		} else if (name.equals("defs")) {
			// generally this will contain gradient info, so may
			// as well just throw it into a group element for parsing
			return new BaseObject(this, elem);

		} else if (name.equals("line")) {
			return new Line(this, elem);

		} else if (name.equals("circle")) {
			return new Circle(this, elem);

		} else if (name.equals("ellipse")) {
			return new Ellipse(this, elem);

		} else if (name.equals("rect")) {
			return new Rect(this, elem);

		} else if (name.equals("polygon")) {
			return new Poly(this, elem, true);

		} else if (name.equals("polyline")) {
			return new Poly(this, elem, false);

		} else if (name.equals("path")) {
			return new Path(this, elem);

		} else if (name.equals("radialGradient")) {
			return new RadialGradient(this, elem);

		} else if (name.equals("linearGradient")) {
			return new LinearGradient(this, elem);

		} else if (name.equals("text")) {
			PApplet.println("Text is not currently handled, " +
			"convert text to outlines instead.");

		} else if (name.equals("filter")) {
			PApplet.println("Filters are not supported.");

		} else if (name.equals("mask")) {
			PApplet.println("Masks are not supported.");

		} else {
			System.err.println("Ignoring  <" + name + "> tag.");
		}
		return null; 
	}


    /**
     * Prints out the SVG document useful for parsing
     */
    public void print() {
        PApplet.println(element.toString());
    }
}
