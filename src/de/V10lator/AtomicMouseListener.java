package de.V10lator;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class AtomicMouseListener implements MouseListener {

    @Override
    public void mouseClicked(MouseEvent event) {
        System.out.println("Mouse click: "+event.getX()+" / "+event.getY());
        if(AtomicRNG.isVideoButton(event.getX(), event.getY()))
            AtomicRNG.toggleRecording();
    }

    @Override
    public void mousePressed(MouseEvent event) {}

    @Override
    public void mouseReleased(MouseEvent event) {}

    @Override
    public void mouseEntered(MouseEvent event) {}

    @Override
    public void mouseExited(MouseEvent event) {}

}
