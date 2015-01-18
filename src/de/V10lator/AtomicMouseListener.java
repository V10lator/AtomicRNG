package de.V10lator;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class AtomicMouseListener implements MouseListener {

    @Override
    public void mouseClicked(MouseEvent event) {
        if(AtomicRNG.isVideoButton(event.getX(), event.getY()))
            AtomicRNG.toggleRecording();
        if(AtomicRNG.isImageButton(event.getX(), event.getY()))
            AtomicRNG.toggleRandomImage();
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
