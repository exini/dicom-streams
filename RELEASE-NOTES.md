# Dicom Streams RELEASE NOTES

## Release 5.0.4

- Fixed bug where switching to indeterminate length sequences and items in big endian files led to inserted 
  delimitations with the wrong endianess (little endian)
